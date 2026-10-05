package com.runiverse.running_service.integration_test.match;

import com.runiverse.running_service.application.auth.command.signup.SignUpCommand;
import com.runiverse.running_service.application.auth.command.signup.SignUpHandler;
import com.runiverse.running_service.application.match.command.apply.ApplyMatchCommand;
import com.runiverse.running_service.application.match.command.apply.ApplyMatchHandler;
import com.runiverse.running_service.application.match.command.apply.MatchRoomAssigner;
import com.runiverse.running_service.application.match.command.broadcast.BroadMatchEventHandler;
import com.runiverse.running_service.application.match.command.broadcast.BroadcastMatchEventCommand;
import com.runiverse.running_service.application.match.command.cancel.CancelMatchCommand;
import com.runiverse.running_service.application.match.command.cancel.CancelMatchHandler;
import com.runiverse.running_service.application.match.common.MatchProperties;
import com.runiverse.running_service.application.match.common.MatchRoomChangedEvent;
import com.runiverse.running_service.application.match.common.RoomInfoAssembler;
import com.runiverse.running_service.application.match.exception.ActiveMatchNotFoundException;
import com.runiverse.running_service.application.match.exception.MatchAlreadyStartedException;
import com.runiverse.running_service.application.match.exception.MatchCooldownException;
import com.runiverse.running_service.application.match.port.out.MatchEventType;
import com.runiverse.running_service.application.match.port.out.MatchStreamEvent;
import com.runiverse.running_service.application.match.port.out.RoomInfo;
import com.runiverse.running_service.application.scheduling.command.schedule.ScheduleJobHandler;
import com.runiverse.running_service.application.user.command.onboarding.CompleteOnboardingCommand;
import com.runiverse.running_service.application.user.command.onboarding.CompleteOnboardingHandler;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.running.player.RunningPlayer;
import com.runiverse.running_service.domain.running.player.vo.RunningPlayerId;
import com.runiverse.running_service.domain.running.player.vo.RunningPlayerStatus;
import com.runiverse.running_service.domain.running.room.RunningRoom;
import com.runiverse.running_service.domain.running.room.SessionDraft;
import com.runiverse.running_service.domain.running.room.vo.RunningRoomId;
import com.runiverse.running_service.domain.running.room.vo.RunningRoomStatus;
import com.runiverse.running_service.domain.running.room.vo.RunningRoomType;
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

@DisplayName("매칭 취소·방 나가기 통합 테스트")
public class CancelMatchIntegrationTest extends IntegrationTestSupport {

    private static final String PASSWORD = "Password123!";
    private static final int AVG_PACE = 330;
    private static final int TARGET_DISTANCE = 5_000;
    private static final LocalDateTime SLOT = LocalDate.now().plusDays(1).atTime(19, 0);   // 마감되지 않는 내일 슬롯

    // 운영 설정과 같은 값
    private static final Duration CLOSE_OFFSET = Duration.ofMinutes(10);
    private static final Duration COOLDOWN = Duration.ofMinutes(20);
    private static final MatchProperties MATCH_PROPERTIES = new MatchProperties(
            CLOSE_OFFSET, Duration.ofSeconds(10), Duration.ofHours(6), 10, COOLDOWN);

    private SignUpHandler signUpHandler;
    private CompleteOnboardingHandler completeOnboardingHandler;
    private InMemoryMatchCooldownStore matchCooldownStore;
    private MatchRoomMemberRegistry matchRoomMemberRegistry;
    private MatchStreamRegistryAdapter matchStreamRegistry;
    private ApplyMatchHandler applyMatchHandler;
    private CancelMatchHandler handler;
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
                // 예약은 이 테스트의 주제가 아니다
                new ScheduleJobHandler(new InMemoryScheduledJobStore(), event -> {
                }),            // ScheduleJobPort
                MATCH_PROPERTIES
        );
        applyMatchHandler = new ApplyMatchHandler(
                matchCooldownStore,  // MatchCooldownPort
                runningStore,        // ExistsActiveApplicationPort
                onboardingStore,     // LoadUserAvgPacePort
                runningStore,        // CreateMatchApplicationPort
                matchRoomAssigner,
                MATCH_PROPERTIES,
                roomInfoAssembler,
                eventPublisher
        );
        handler = new CancelMatchHandler(
                runningStore,        // LockMatchApplicationPort
                matchStore,          // LoadMatchRoomPort
                runningStore,        // LockMatchRoomPort
                runningStore,        // UpdateMatchApplicationPort
                runningStore,        // UpdateMatchRoomPort
                matchCooldownStore,  // MatchCooldownPort
                MATCH_PROPERTIES,
                roomInfoAssembler,
                eventPublisher
        );
    }

    @Test
    @DisplayName("마감 전 취소는 제재 없이 신청을 끝내고 남은 참가자에게 줄어든 인원을 알린다")
    void cancelsBeforeCloseWithoutPenalty() {
        // given -> 두 명이 대기 중이고 남을 사람이 스트림을 열어 두었다
        UUID stayerId = onboardedUser();
        UUID leaverId = onboardedUser();
        Long runningRoomId = apply(stayerId);
        apply(leaverId);
        RecordingMatchStreamConnection stayerConnection = connect(stayerId, runningRoomId);

        // when
        cancel(leaverId);

        // then -> 신청은 끝났고 사유가 status에 남는다
        RunningPlayer left = playerOf(leaverId, runningRoomId);
        assertThat(left.getStatus()).isEqualTo(RunningPlayerStatus.MATCHED_LEFT_NO_PENALTY);
        assertThat(left.getDeletedAt()).isPresent();
        assertThat(matchCooldownStore.until(new UserId(leaverId))).isEmpty();
        // 방은 남은 한 명으로 계속 모집한다
        RunningRoom room = runningStore.findRoom(runningRoomId).orElseThrow();
        assertThat(room.getStatus()).isEqualTo(RunningRoomStatus.MATCHING);
        assertThat(room.getPlayerCount().current()).isEqualTo(1);
        assertThat(lastRoomOf(stayerConnection).players())
                .extracting(RoomInfo.RoomPlayer::userId)
                .containsExactly(stayerId);
    }

    @Test
    @DisplayName("대기 취소 뒤에는 곧바로 다시 신청할 수 있고 거쳐 간 방에도 다시 붙는다")
    void reappliesRightAfterCancel() {
        // given
        UUID stayerId = onboardedUser();
        UUID leaverId = onboardedUser();
        Long runningRoomId = apply(stayerId);
        apply(leaverId);
        RunningPlayerId canceledPlayerId = activePlayerIdOf(leaverId);
        cancel(leaverId);

        // when -> 이탈 이력은 순위만 낮출 뿐 문을 잠그지 않는다
        Long rejoinedRoomId = apply(leaverId);

        // then
        assertThat(rejoinedRoomId).isEqualTo(runningRoomId);
        RunningRoom room = runningStore.findRoom(runningRoomId).orElseThrow();
        assertThat(room.getPlayerCount().current()).isEqualTo(2);
        // 세션은 새로 만들지 않고 되살려 새 신청에 꽂는다 — 끝난 신청을 가리키면 안 된다
        RunningPlayerId rejoinedPlayerId = activePlayerIdOf(leaverId);
        assertThat(rejoinedPlayerId).isNotEqualTo(canceledPlayerId);
        assertThat(room.getSessions())
                .filteredOn(session -> session.getUserId().equals(new UserId(leaverId)))
                .singleElement()
                .satisfies(session -> {
                    assertThat(session.isConnected()).isTrue();
                    assertThat(session.getRunningPlayerId()).isEqualTo(rejoinedPlayerId);
                    assertThat(session.getLeaveCount().value()).isEqualTo(1);
                });
    }

    @Test
    @DisplayName("마지막 참가자가 나가면 방이 취소되고 받을 사람이 없어 알리지 않는다")
    void lastLeaverCancelsRoom() {
        // given
        UUID userId = onboardedUser();
        Long runningRoomId = apply(userId);
        RecordingMatchStreamConnection connection = connect(userId, runningRoomId);

        // when
        cancel(userId);

        // then
        RunningRoom room = runningStore.findRoom(runningRoomId).orElseThrow();
        assertThat(room.getStatus()).isEqualTo(RunningRoomStatus.CANCELLED);
        assertThat(room.getPlayerCount().current()).isZero();
        assertThat(room.getCloseAt()).isPresent();
        assertThat(connection.received()).isEmpty();
    }

    @Test
    @DisplayName("마감 뒤 둘 이상 남은 방을 나가면 제재가 걸려 재신청이 막힌다")
    void leavingAfterCloseIsPenalized() {
        // given -> 마감(시작 10분 전)이 지나 확정된 2인 방이다
        UUID stayerId = onboardedUser();
        UUID leaverId = onboardedUser();
        Long runningRoomId = givenMatchedRoom(List.of(stayerId, leaverId));
        RecordingMatchStreamConnection stayerConnection = connect(stayerId, runningRoomId);

        // when
        cancel(leaverId);

        // then -> 혼자 남아도 방은 취소하지 않는다
        assertThat(playerOf(leaverId, runningRoomId).getStatus())
                .isEqualTo(RunningPlayerStatus.MATCHED_LEFT_PENALTY);
        assertThat(matchCooldownStore.until(new UserId(leaverId))).isPresent();
        RunningRoom room = runningStore.findRoom(runningRoomId).orElseThrow();
        assertThat(room.getStatus()).isEqualTo(RunningRoomStatus.MATCHED);
        assertThat(room.getPlayerCount().current()).isEqualTo(1);
        assertThat(lastRoomOf(stayerConnection).players())
                .extracting(RoomInfo.RoomPlayer::userId)
                .containsExactly(stayerId);
        assertThatThrownBy(() -> apply(leaverId))
                .isInstanceOf(MatchCooldownException.class);
    }

    @Test
    @DisplayName("마감 뒤라도 혼자 남은 방을 나가는 것은 제재하지 않는다")
    void leavingSoloLeftRoomIsNotPenalized() {
        // given -> 혼자 확정된 방이다. 손해를 보는 상대가 없다
        UUID userId = onboardedUser();
        Long runningRoomId = givenMatchedRoom(List.of(userId));

        // when
        cancel(userId);

        // then
        assertThat(playerOf(userId, runningRoomId).getStatus())
                .isEqualTo(RunningPlayerStatus.MATCHED_LEFT_NO_PENALTY);
        assertThat(matchCooldownStore.until(new UserId(userId))).isEmpty();
        assertThat(runningStore.findRoom(runningRoomId).orElseThrow().getStatus())
                .isEqualTo(RunningRoomStatus.CANCELLED);
    }

    @Test
    @DisplayName("러닝이 시작된 뒤에는 취소로 끊을 수 없다")
    void rejectsAfterRunningStarted() {
        // given -> 시작 시각이 지나 방이 STARTED고 본인도 뛰는 중이다
        UUID userId = onboardedUser();
        UUID otherId = onboardedUser();
        Long runningRoomId = givenRoom(RunningRoomStatus.STARTED,
                LocalDateTime.now().minusMinutes(5), List.of(userId, otherId));

        // when & then
        assertThatThrownBy(() -> cancel(userId))
                .isInstanceOf(MatchAlreadyStartedException.class);
        assertThat(runningStore.loadActive(new UserId(userId))).isPresent();
        RunningRoom room = runningStore.findRoom(runningRoomId).orElseThrow();
        assertThat(room.getStatus()).isEqualTo(RunningRoomStatus.STARTED);
        assertThat(room.getPlayerCount().current()).isEqualTo(2);
    }

    @Test
    @DisplayName("활성 신청이 없으면 취소할 것이 없다")
    void rejectsWithoutApplication() {
        // given
        UUID userId = onboardedUser();

        // when & then
        assertThatThrownBy(() -> cancel(userId))
                .isInstanceOf(ActiveMatchNotFoundException.class);
    }

    private Long apply(UUID userId) {
        return applyMatchHandler.handle(
                new ApplyMatchCommand(userId, SLOT, TARGET_DISTANCE)).runningRoomId();
    }

    private void cancel(UUID userId) {
        handler.handle(new CancelMatchCommand(userId));
    }

    private UUID onboardedUser() {
        userSequence++;
        UUID userId = signUpHandler.handle(new SignUpCommand(
                issueVerificationTicket("runner" + userSequence + "@runiverse.com"), PASSWORD)).userId();
        completeOnboardingHandler.handle(new CompleteOnboardingCommand(
                userId, "러너" + userSequence, "MALE", LocalDate.of(1998, 5, 20),
                AVG_PACE, new BigDecimal("70.0"), new BigDecimal("175.0")));
        return userId;
    }

    // 마감이 지난 방은 신청으로 만들 수 없다(마감 슬롯은 막힌다) — 확정된 방을 직접 둔다
    private Long givenMatchedRoom(List<UUID> userIds) {
        return givenRoom(RunningRoomStatus.MATCHED,
                LocalDateTime.now().plus(CLOSE_OFFSET).minusMinutes(1), userIds);
    }

    // 시작된 방이면 참가자도 실제 시작 경로처럼 RUNNING으로 둔다
    private Long givenRoom(RunningRoomStatus status, LocalDateTime startAt, List<UUID> userIds) {
        List<SessionDraft> sessions = new ArrayList<>();
        for (UUID userId : userIds) {
            RunningPlayer request = RunningPlayer.request(userId, AVG_PACE, TARGET_DISTANCE, startAt);
            if (status == RunningRoomStatus.STARTED) {
                request.start();
            }
            RunningPlayer player = runningStore.create(request);
            sessions.add(new SessionDraft(
                    new UserId(userId), player.getRunningPlayerId().orElseThrow(), 0, true));
        }
        RunningRoom room = runningStore.create(RunningRoom.builder()
                .type(RunningRoomType.MATCH)
                .status(status)
                .startAt(startAt)
                .targetDistance(TARGET_DISTANCE)
                .avgPace(AVG_PACE)
                .currentPlayerCount(userIds.size())
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

    private RunningPlayerId activePlayerIdOf(UUID userId) {
        return runningStore.loadActive(new UserId(userId)).orElseThrow()
                .getRunningPlayerId().orElseThrow();
    }

    // 끝난 신청도 찾는다
    private RunningPlayer playerOf(UUID userId, Long runningRoomId) {
        return runningStore.load(new RunningRoomId(runningRoomId), new UserId(userId)).orElseThrow();
    }

    private RoomInfo lastRoomOf(RecordingMatchStreamConnection connection) {
        List<MatchStreamEvent> received = connection.received();
        assertThat(received).isNotEmpty();
        MatchStreamEvent last = received.get(received.size() - 1);
        assertThat(last.type()).isEqualTo(MatchEventType.MATCH_ROOM_UPDATED);
        return last.room();
    }
}
