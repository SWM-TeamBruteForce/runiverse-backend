package com.runiverse.running_service.integration_test.match;

import com.github.f4b6a3.uuid.UuidCreator;
import com.runiverse.running_service.application.auth.command.signup.SignUpCommand;
import com.runiverse.running_service.application.auth.command.signup.SignUpHandler;
import com.runiverse.running_service.application.match.command.apply.ApplyMatchCommand;
import com.runiverse.running_service.application.match.command.apply.ApplyMatchHandler;
import com.runiverse.running_service.application.match.command.apply.MatchRoomAssigner;
import com.runiverse.running_service.application.match.command.broadcast.BroadMatchEventHandler;
import com.runiverse.running_service.application.match.command.broadcast.BroadcastMatchEventCommand;
import com.runiverse.running_service.application.match.common.MatchProperties;
import com.runiverse.running_service.application.match.common.MatchRoomChangedEvent;
import com.runiverse.running_service.application.match.common.RoomInfoAssembler;
import com.runiverse.running_service.application.match.exception.MatchAlreadyInProgressException;
import com.runiverse.running_service.application.match.exception.MatchCooldownException;
import com.runiverse.running_service.application.match.exception.MatchSlotClosedException;
import com.runiverse.running_service.application.match.port.out.MatchEventType;
import com.runiverse.running_service.application.match.port.out.MatchStreamEvent;
import com.runiverse.running_service.application.match.port.out.RoomInfo;
import com.runiverse.running_service.application.scheduling.command.schedule.ScheduleJobHandler;
import com.runiverse.running_service.application.user.command.onboarding.CompleteOnboardingCommand;
import com.runiverse.running_service.application.user.command.onboarding.CompleteOnboardingHandler;
import com.runiverse.running_service.application.user.exception.OnboardingNotCompletedException;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.running.metric.vo.Distance;
import com.runiverse.running_service.domain.running.metric.vo.Pace;
import com.runiverse.running_service.domain.running.player.RunningPlayer;
import com.runiverse.running_service.domain.running.player.vo.RunningPlayerId;
import com.runiverse.running_service.domain.running.room.RunningRoom;
import com.runiverse.running_service.domain.running.room.SessionDraft;
import com.runiverse.running_service.domain.running.room.vo.RunningRoomStatus;
import com.runiverse.running_service.domain.running.room.vo.RunningRoomType;
import com.runiverse.running_service.domain.scheduling.vo.ScheduledJobType;
import com.runiverse.running_service.infrastructure.sse.MatchRoomMemberRegistry;
import com.runiverse.running_service.infrastructure.sse.MatchStreamRegistryAdapter;
import com.runiverse.running_service.integration_test.IntegrationTestSupport;
import com.runiverse.running_service.integration_test.fake.FakeViewUrlGenerator;
import com.runiverse.running_service.integration_test.fake.InMemoryMatchCooldownStore;
import com.runiverse.running_service.integration_test.fake.InMemoryMatchStore;
import com.runiverse.running_service.integration_test.fake.InMemoryPlayerProfileStore;
import com.runiverse.running_service.integration_test.fake.InMemoryScheduledJobStore;
import com.runiverse.running_service.integration_test.fake.RecordingMatchStreamConnection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static java.time.temporal.ChronoUnit.SECONDS;

@DisplayName("매칭 신청 통합 테스트")
public class ApplyMatchIntegrationTest extends IntegrationTestSupport {

    private static final String PASSWORD = "Password123!";
    private static final int TARGET_DISTANCE = 5_000;
    private static final LocalDateTime SLOT = LocalDate.now().plusDays(1).atTime(19, 0);   // 마감되지 않는 내일 슬롯

    // 운영 설정과 같은 값
    private static final Duration CLOSE_OFFSET = Duration.ofMinutes(10);
    private static final Duration READY_OFFSET = Duration.ofSeconds(10);
    private static final Duration FORCE_FINISH_OFFSET = Duration.ofHours(6);
    private static final Duration COOLDOWN = Duration.ofMinutes(20);
    private static final MatchProperties MATCH_PROPERTIES = new MatchProperties(
            CLOSE_OFFSET, READY_OFFSET, FORCE_FINISH_OFFSET, 10, COOLDOWN);

    private SignUpHandler signUpHandler;
    private CompleteOnboardingHandler completeOnboardingHandler;
    private InMemoryMatchCooldownStore matchCooldownStore;
    private InMemoryScheduledJobStore scheduledJobStore;
    private MatchRoomMemberRegistry matchRoomMemberRegistry;
    private MatchStreamRegistryAdapter matchStreamRegistry;
    private ApplyMatchHandler handler;
    private int userSequence;   // 이메일·닉네임 중복을 피한다

    @BeforeEach
    void setUp() {
        signUpHandler = newSignUpHandler();
        completeOnboardingHandler = new CompleteOnboardingHandler(
                userStore,        // LoadUserByIdPort
                onboardingStore,  // ExistsOnboardingPort
                onboardingStore,  // CheckNicknameDuplicatePort
                onboardingStore   // SaveOnboardingPort
        );
        matchCooldownStore = new InMemoryMatchCooldownStore();
        scheduledJobStore = new InMemoryScheduledJobStore();
        matchRoomMemberRegistry = new MatchRoomMemberRegistry();
        matchStreamRegistry = new MatchStreamRegistryAdapter();

        InMemoryMatchStore matchStore = new InMemoryMatchStore(runningStore);
        BroadMatchEventHandler broadMatchEventHandler =
                new BroadMatchEventHandler(matchRoomMemberRegistry, matchStreamRegistry);
        // MatchEventDispatcher(AFTER_COMMIT) + Redis 왕복 + MatchEventListener를 대신한다 —
        // 발행된 이벤트가 곧바로 전파 단계로 들어간다
        ApplicationEventPublisher eventPublisher = event -> {
            if (event instanceof MatchRoomChangedEvent changed) {
                broadMatchEventHandler.handle(new BroadcastMatchEventCommand(changed.event()));
            }
        };
        RoomInfoAssembler roomInfoAssembler = new RoomInfoAssembler(
                matchStore,                                                 // LoadMatchPlayersPort
                new InMemoryPlayerProfileStore(userStore, onboardingStore), // LoadPlayerProfilesPort
                new FakeViewUrlGenerator(),                                 // GenerateViewUrlPort
                MATCH_PROPERTIES,
                runningStore,                                               // LoadActiveApplicationPort
                matchStore,                                                 // LoadMatchRoomPort
                runningStore                                                // LoadMatchRoomDetailPort
        );
        MatchRoomAssigner matchRoomAssigner = new MatchRoomAssigner(
                matchStore,    // LoadMatchCandidatesPort
                runningStore,  // LockMatchRoomPort
                matchStore,    // LoadMatchPlayersPort
                runningStore,  // UpdateMatchRoomPort
                runningStore,  // CreateMatchRoomPort
                // 타이머 등록은 이 테스트의 주제가 아니다 — 예약 행만 남긴다
                new ScheduleJobHandler(scheduledJobStore, event -> {
                }),            // ScheduleJobPort
                MATCH_PROPERTIES
        );
        handler = new ApplyMatchHandler(
                matchCooldownStore,  // MatchCooldownPort
                runningStore,        // ExistsActiveApplicationPort
                onboardingStore,     // LoadUserAvgPacePort
                runningStore,        // CreateMatchApplicationPort
                matchRoomAssigner,
                MATCH_PROPERTIES,
                roomInfoAssembler,
                eventPublisher
        );
    }

    @Test
    @DisplayName("붙을 방이 없으면 1인 방을 열고 마감·준비·시작·강제 종료를 예약한다")
    void opensSingleRoomWithSchedules() {
        // given
        UUID userId = onboardedUser(330);

        // when
        Long runningRoomId = apply(userId, SLOT, TARGET_DISTANCE);

        // then -> 방의 평균 페이스는 신청자 온보딩 값이다
        RunningRoom room = runningStore.findRoom(runningRoomId).orElseThrow();
        assertThat(room.getType()).isEqualTo(RunningRoomType.MATCH);
        assertThat(room.getStatus()).isEqualTo(RunningRoomStatus.MATCHING);
        assertThat(room.getStartAt()).isEqualTo(SLOT);
        assertThat(room.getTargetDistance()).contains(new Distance(TARGET_DISTANCE));
        assertThat(room.getPlayerCount().current()).isEqualTo(1);
        assertThat(room.getAvgPace()).contains(new Pace(330));
        assertThat(executeAtOf(ScheduledJobType.MATCH_CLOSE, runningRoomId))
                .isEqualTo(SLOT.minus(CLOSE_OFFSET));
        assertThat(executeAtOf(ScheduledJobType.RUNNING_READY, runningRoomId))
                .isEqualTo(SLOT.minus(READY_OFFSET));
        assertThat(executeAtOf(ScheduledJobType.RUNNING_START, runningRoomId)).isEqualTo(SLOT);
        assertThat(executeAtOf(ScheduledJobType.RUNNING_FORCE_FINISH, runningRoomId))
                .isEqualTo(SLOT.plus(FORCE_FINISH_OFFSET));
        assertThat(runningStore.loadActive(new UserId(userId))).isPresent();
    }

    @Test
    @DisplayName("같은 슬롯·거리의 두 번째 신청은 같은 방에 붙고 기존 참가자에게 갱신이 간다")
    void joinsExistingRoomAndNotifiesMembers() {
        // given -> 첫 신청자가 스트림을 열어 두었다
        UUID hostId = onboardedUser(330);
        Long runningRoomId = apply(hostId, SLOT, TARGET_DISTANCE);
        RecordingMatchStreamConnection hostConnection = connect(hostId, runningRoomId);
        UUID guestId = onboardedUser(390);

        // when
        Long joinedRoomId = apply(guestId, SLOT, TARGET_DISTANCE);

        // then -> 인원과 평균 페이스가 함께 갱신된다
        assertThat(joinedRoomId).isEqualTo(runningRoomId);
        RunningRoom room = runningStore.findRoom(runningRoomId).orElseThrow();
        assertThat(room.getPlayerCount().current()).isEqualTo(2);
        assertThat(room.getAvgPace()).contains(new Pace(360));
        assertThat(runningStore.roomCount()).isEqualTo(1);

        RoomInfo updated = lastRoomOf(hostConnection);
        assertThat(updated.runningRoomId()).isEqualTo(runningRoomId);
        assertThat(updated.players())
                .extracting(RoomInfo.RoomPlayer::userId)
                .containsExactly(hostId, guestId);
        assertThat(updated.teamAveragePaceSecondsPerKm()).isEqualTo(360);
        // 예약은 방이 생길 때만 건다 — 합류로 늘지 않는다
        assertThat(scheduledJobStore.jobCount()).isEqualTo(4);
    }

    @Test
    @DisplayName("슬롯이나 거리가 다르면 같은 방에 붙지 않는다")
    void differentConditionOpensAnotherRoom() {
        // given
        Long runningRoomId = apply(onboardedUser(330), SLOT, TARGET_DISTANCE);

        // when
        Long otherSlotRoomId = apply(onboardedUser(330), SLOT.plusMinutes(30), TARGET_DISTANCE);
        Long otherDistanceRoomId = apply(onboardedUser(330), SLOT, 10_000);

        // then
        assertThat(otherSlotRoomId).isNotEqualTo(runningRoomId);
        assertThat(otherDistanceRoomId).isNotEqualTo(runningRoomId).isNotEqualTo(otherSlotRoomId);
        assertThat(runningStore.roomCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("방이 가득 차면 다음 신청자는 새 방을 연다")
    void fullRoomIsSkipped() {
        // given -> 4명이 차례로 신청해 한 방을 채웠다
        Long fullRoomId = apply(onboardedUser(330), SLOT, TARGET_DISTANCE);
        for (int i = 0; i < 3; i++) {
            apply(onboardedUser(330), SLOT, TARGET_DISTANCE);
        }
        assertThat(runningStore.findRoom(fullRoomId).orElseThrow().getPlayerCount().current())
                .isEqualTo(4);

        // when
        Long nextRoomId = apply(onboardedUser(330), SLOT, TARGET_DISTANCE);

        // then
        assertThat(nextRoomId).isNotEqualTo(fullRoomId);
        assertThat(runningStore.findRoom(nextRoomId).orElseThrow().getPlayerCount().current())
                .isEqualTo(1);
    }

    @Test
    @DisplayName("후보가 여럿이면 내 페이스에 가까운 방에 붙는다")
    void prefersRoomWithCloserPace() {
        // given -> 같은 슬롯에 방이 둘이다. 붙을 방이 있으면 붙으므로 신청만으로는 못 만든다
        givenMatchingRoom(300, List.of());
        Long closerRoomId = givenMatchingRoom(400, List.of());

        // when
        Long assignedRoomId = apply(onboardedUser(390), SLOT, TARGET_DISTANCE);

        // then
        assertThat(assignedRoomId).isEqualTo(closerRoomId);
    }

    @Test
    @DisplayName("페이스가 비슷하면 내가 덜 나갔던 방에 붙는다")
    void prefersRoomLeftLessOften() {
        // given -> 먼저 생긴 방은 이 신청자가 한 번 나갔던 방이다. 이력이 없으면 오래된 방이 앞선다
        UUID userId = onboardedUser(330);
        givenMatchingRoom(330, List.of(new SessionDraft(
                new UserId(userId), new RunningPlayerId(9_999L), 1, false)));
        Long freshRoomId = givenMatchingRoom(330, List.of());

        // when
        Long assignedRoomId = apply(userId, SLOT, TARGET_DISTANCE);

        // then
        assertThat(assignedRoomId).isEqualTo(freshRoomId);
    }

    @Test
    @DisplayName("모집이 마감된 슬롯은 신청을 남기지 않고 막는다")
    void rejectsClosedSlot() {
        // given -> 마감(시작 10분 전)이 이미 지났다
        UUID userId = onboardedUser(330);
        LocalDateTime closedSlot = LocalDateTime.now().plus(CLOSE_OFFSET).minusMinutes(1);

        // when & then
        assertThatThrownBy(() -> apply(userId, closedSlot, TARGET_DISTANCE))
                .isInstanceOf(MatchSlotClosedException.class);
        assertThat(runningStore.playerCount()).isZero();
        assertThat(runningStore.roomCount()).isZero();
    }

    @Test
    @DisplayName("제재 쿨다운 중이면 해제 시각과 함께 막는다")
    void rejectsDuringCooldown() {
        // given
        UUID userId = onboardedUser(330);
        matchCooldownStore.start(new UserId(userId), COOLDOWN);

        // when & then -> 409 응답의 cooldownUntil이 이 값이다
        assertThatThrownBy(() -> apply(userId, SLOT, TARGET_DISTANCE))
                .isInstanceOfSatisfying(MatchCooldownException.class, e ->
                        assertThat(e.getCooldownUntil())
                                .isCloseTo(LocalDateTime.now().plus(COOLDOWN), within(5, SECONDS)));
        assertThat(runningStore.playerCount()).isZero();
    }

    @Test
    @DisplayName("활성 신청이 있으면 다른 슬롯이어도 막는다")
    void rejectsSecondApplication() {
        // given
        UUID userId = onboardedUser(330);
        apply(userId, SLOT, TARGET_DISTANCE);

        // when & then
        assertThatThrownBy(() -> apply(userId, SLOT.plusMinutes(30), TARGET_DISTANCE))
                .isInstanceOf(MatchAlreadyInProgressException.class);
        assertThat(runningStore.playerCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("온보딩 전에는 매칭 조건에 쓸 페이스가 없어 막는다")
    void rejectsWithoutOnboarding() {
        // given
        UUID userId = signUpHandler.handle(new SignUpCommand(
                issueVerificationTicket("pending@runiverse.com"), PASSWORD)).userId();

        // when & then
        assertThatThrownBy(() -> apply(userId, SLOT, TARGET_DISTANCE))
                .isInstanceOf(OnboardingNotCompletedException.class);
        assertThat(runningStore.playerCount()).isZero();
    }

    private Long apply(UUID userId, LocalDateTime scheduledStartAt, int targetDistanceMeters) {
        return handler.handle(
                new ApplyMatchCommand(userId, scheduledStartAt, targetDistanceMeters)).runningRoomId();
    }

    private UUID onboardedUser(int averagePaceSecondsPerKm) {
        userSequence++;
        UUID userId = signUpHandler.handle(new SignUpCommand(
                issueVerificationTicket("runner" + userSequence + "@runiverse.com"), PASSWORD)).userId();
        completeOnboardingHandler.handle(new CompleteOnboardingCommand(
                userId, "러너" + userSequence, "MALE", LocalDate.of(1998, 5, 20),
                averagePaceSecondsPerKm, new BigDecimal("70.0"), new BigDecimal("175.0")));
        return userId;
    }

    // 모집 중인 1인 방을 직접 둔다. 거쳐 간 참가자의 이탈 이력은 끊긴 세션으로 싣는다
    private Long givenMatchingRoom(int avgPace, List<SessionDraft> leftSessions) {
        UUID hostId = UuidCreator.getTimeOrderedEpoch();
        RunningPlayer host = runningStore.create(
                RunningPlayer.request(hostId, avgPace, TARGET_DISTANCE, SLOT));
        List<SessionDraft> sessions = new ArrayList<>(leftSessions);
        sessions.add(new SessionDraft(
                new UserId(hostId), host.getRunningPlayerId().orElseThrow(), 0, true));
        RunningRoom room = runningStore.create(RunningRoom.builder()
                .type(RunningRoomType.MATCH)
                .status(RunningRoomStatus.MATCHING)
                .startAt(SLOT)
                .targetDistance(TARGET_DISTANCE)
                .avgPace(avgPace)
                .currentPlayerCount(1)
                .maxPlayerCount(4)
                .sessions(sessions)
                .build());
        return room.getRunningRoomId().orElseThrow().value();
    }

    // 스트림 연결 핸들러 대신 레지스트리에 직접 붙인다 — 연결 자체는 이 테스트의 주제가 아니다
    private RecordingMatchStreamConnection connect(UUID userId, Long runningRoomId) {
        RecordingMatchStreamConnection connection = new RecordingMatchStreamConnection("conn-" + userId);
        matchStreamRegistry.register(new UserId(userId), connection);
        matchRoomMemberRegistry.join(new UserId(userId), runningRoomId);
        return connection;
    }

    private RoomInfo lastRoomOf(RecordingMatchStreamConnection connection) {
        List<MatchStreamEvent> received = connection.received();
        assertThat(received).isNotEmpty();
        MatchStreamEvent last = received.get(received.size() - 1);
        assertThat(last.type()).isEqualTo(MatchEventType.MATCH_ROOM_UPDATED);
        return last.room();
    }

    private LocalDateTime executeAtOf(ScheduledJobType type, Long runningRoomId) {
        return scheduledJobStore.findBy(type, runningRoomId).orElseThrow().getExecuteAt();
    }
}
