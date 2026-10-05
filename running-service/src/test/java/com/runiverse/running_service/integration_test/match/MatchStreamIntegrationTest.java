package com.runiverse.running_service.integration_test.match;

import com.runiverse.running_service.application.auth.command.signup.SignUpCommand;
import com.runiverse.running_service.application.auth.command.signup.SignUpHandler;
import com.runiverse.running_service.application.match.command.apply.ApplyMatchCommand;
import com.runiverse.running_service.application.match.command.apply.ApplyMatchHandler;
import com.runiverse.running_service.application.match.command.apply.MatchRoomAssigner;
import com.runiverse.running_service.application.match.command.broadcast.BroadMatchEventHandler;
import com.runiverse.running_service.application.match.command.broadcast.BroadcastMatchEventCommand;
import com.runiverse.running_service.application.match.command.stream.CloseMatchStreamCommand;
import com.runiverse.running_service.application.match.command.stream.CloseMatchStreamHandler;
import com.runiverse.running_service.application.match.command.stream.OpenMatchStreamCommand;
import com.runiverse.running_service.application.match.command.stream.OpenMatchStreamHandler;
import com.runiverse.running_service.application.match.common.MatchProperties;
import com.runiverse.running_service.application.match.common.MatchRoomChangedEvent;
import com.runiverse.running_service.application.match.common.RoomInfoAssembler;
import com.runiverse.running_service.application.match.exception.ActiveMatchNotFoundException;
import com.runiverse.running_service.application.match.port.out.MatchEventType;
import com.runiverse.running_service.application.match.port.out.MatchStreamEvent;
import com.runiverse.running_service.application.match.port.out.RoomInfo;
import com.runiverse.running_service.application.scheduling.command.schedule.ScheduleJobHandler;
import com.runiverse.running_service.application.user.command.onboarding.CompleteOnboardingCommand;
import com.runiverse.running_service.application.user.command.onboarding.CompleteOnboardingHandler;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.running.room.vo.RunningRoomStatus;
import com.runiverse.running_service.infrastructure.sse.MatchRoomMemberRegistry;
import com.runiverse.running_service.infrastructure.sse.MatchStreamRegistryAdapter;
import com.runiverse.running_service.integration_test.IntegrationTestSupport;
import com.runiverse.running_service.integration_test.fake.FakeMatchRoomMembership;
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
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("매칭 이벤트 스트림 연결·종료 통합 테스트")
public class MatchStreamIntegrationTest extends IntegrationTestSupport {

    private static final String PASSWORD = "Password123!";
    private static final int AVG_PACE = 330;
    private static final int TARGET_DISTANCE = 5_000;
    private static final LocalDateTime SLOT = LocalDate.now().plusDays(1).atTime(19, 0);   // 마감되지 않는 내일 슬롯

    // 운영 설정과 같은 값
    private static final Duration CLOSE_OFFSET = Duration.ofMinutes(10);
    private static final MatchProperties MATCH_PROPERTIES = new MatchProperties(
            CLOSE_OFFSET, Duration.ofSeconds(10), Duration.ofHours(6), 10, Duration.ofMinutes(20));

    private SignUpHandler signUpHandler;
    private CompleteOnboardingHandler completeOnboardingHandler;
    private MatchRoomMemberRegistry matchRoomMemberRegistry;
    private MatchStreamRegistryAdapter matchStreamRegistry;
    private FakeMatchRoomMembership matchRoomMembership;
    private ApplyMatchHandler applyMatchHandler;
    private OpenMatchStreamHandler openHandler;
    private CloseMatchStreamHandler closeHandler;
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
        matchRoomMemberRegistry = new MatchRoomMemberRegistry();
        matchStreamRegistry = new MatchStreamRegistryAdapter();
        matchRoomMembership = new FakeMatchRoomMembership(matchRoomMemberRegistry);

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
                new InMemoryMatchCooldownStore(),  // MatchCooldownPort
                runningStore,                      // ExistsActiveApplicationPort
                onboardingStore,                   // LoadUserAvgPacePort
                runningStore,                      // CreateMatchApplicationPort
                matchRoomAssigner,
                MATCH_PROPERTIES,
                roomInfoAssembler,
                eventPublisher
        );
        openHandler = new OpenMatchStreamHandler(
                matchStreamRegistry,   // MatchStreamPort
                matchRoomMembership,   // MatchRoomMembershipPort
                roomInfoAssembler
        );
        closeHandler = new CloseMatchStreamHandler(
                matchStreamRegistry,   // MatchStreamPort
                matchRoomMembership    // MatchRoomMembershipPort
        );
    }

    @Test
    @DisplayName("연결하면 현재 방 스냅샷을 한 번 받고 방 채널 구독이 시작된다")
    void sendsSnapshotOnOpen() {
        // given
        UUID userId = onboardedUser();
        Long runningRoomId = apply(userId);
        RecordingMatchStreamConnection connection = connection("conn-1");

        // when
        open(userId, connection);

        // then
        assertThat(connection.received()).hasSize(1);
        MatchStreamEvent snapshot = connection.received().get(0);
        assertThat(snapshot.type()).isEqualTo(MatchEventType.MATCH_ROOM_UPDATED);
        RoomInfo room = snapshot.room();
        assertThat(room.runningRoomId()).isEqualTo(runningRoomId);
        assertThat(room.status()).isEqualTo(RunningRoomStatus.MATCHING);
        assertThat(room.closeAt()).isEqualTo(SLOT.minus(CLOSE_OFFSET));
        assertThat(room.players()).extracting(RoomInfo.RoomPlayer::userId).containsExactly(userId);
        assertThat(matchStreamRegistry.find(new UserId(userId))).contains(connection);
        assertThat(matchRoomMemberRegistry.usersIn(runningRoomId)).containsExactly(new UserId(userId));
        assertThat(matchRoomMembership.isSubscribed(runningRoomId)).isTrue();
    }

    @Test
    @DisplayName("연결해 둔 방에 다른 사람이 붙으면 갱신이 이 연결로 온다")
    void receivesUpdatesAfterOpen() {
        // given
        UUID hostId = onboardedUser();
        Long runningRoomId = apply(hostId);
        RecordingMatchStreamConnection connection = connection("conn-host");
        open(hostId, connection);
        UUID guestId = onboardedUser();

        // when
        apply(guestId);

        // then -> 스냅샷 다음에 인원이 늘어난 방 정보가 온다
        List<MatchStreamEvent> received = connection.received();
        assertThat(received).hasSize(2);
        // 인원 변동은 갱신이다 — 확정(MATCH_STARTED)은 마감 때만 나간다
        assertThat(received.get(1).type()).isEqualTo(MatchEventType.MATCH_ROOM_UPDATED);
        assertThat(received.get(1).room().runningRoomId()).isEqualTo(runningRoomId);
        assertThat(received.get(1).room().players())
                .extracting(RoomInfo.RoomPlayer::userId)
                .containsExactly(hostId, guestId);
    }

    @Test
    @DisplayName("활성 신청이 없으면 연결을 거절하고 레지스트리에 흔적을 남기지 않는다")
    void rejectsWithoutApplication() {
        // given
        UUID userId = onboardedUser();
        RecordingMatchStreamConnection connection = connection("conn-1");

        // when & then -> 열어 주면 어느 방도 구독하지 못해 이벤트가 영영 오지 않는 연결이 된다
        assertThatThrownBy(() -> open(userId, connection))
                .isInstanceOf(ActiveMatchNotFoundException.class);
        assertThat(matchStreamRegistry.find(new UserId(userId))).isEmpty();
        assertThat(connection.received()).isEmpty();
    }

    @Test
    @DisplayName("다시 연결하면 옛 연결을 닫고 새 연결만 남긴다")
    void replacesPreviousConnection() {
        // given -> 앱 재시작처럼 끊긴 줄 모르고 남아 있는 연결이 있다
        UUID userId = onboardedUser();
        apply(userId);
        RecordingMatchStreamConnection previous = connection("conn-old");
        open(userId, previous);
        RecordingMatchStreamConnection current = connection("conn-new");

        // when
        open(userId, current);

        // then
        assertThat(previous.isClosed()).isTrue();
        assertThat(current.isClosed()).isFalse();
        assertThat(current.received()).hasSize(1);
        assertThat(matchStreamRegistry.find(new UserId(userId))).contains(current);
    }

    @Test
    @DisplayName("밀려난 옛 연결의 종료가 늦게 와도 새 연결과 방 구독은 그대로다")
    void lateCloseOfPreviousConnectionKeepsCurrent() {
        // given
        UUID userId = onboardedUser();
        Long runningRoomId = apply(userId);
        RecordingMatchStreamConnection previous = connection("conn-old");
        open(userId, previous);
        RecordingMatchStreamConnection current = connection("conn-new");
        open(userId, current);

        // when -> 옛 연결의 onCompletion이 새 연결 등록 뒤에 도착했다
        close(userId, previous);

        // then
        assertThat(matchStreamRegistry.find(new UserId(userId))).contains(current);
        assertThat(matchRoomMemberRegistry.usersIn(runningRoomId)).containsExactly(new UserId(userId));
        assertThat(matchRoomMembership.isSubscribed(runningRoomId)).isTrue();
    }

    @Test
    @DisplayName("닫으면 방에서 빠지고, 남은 참가자가 있으면 구독은 유지된다")
    void closeLeavesRoomAndKeepsSubscriptionForOthers() {
        // given -> 같은 방의 두 사람이 각자 연결해 두었다
        UUID hostId = onboardedUser();
        UUID guestId = onboardedUser();
        Long runningRoomId = apply(hostId);
        apply(guestId);
        RecordingMatchStreamConnection hostConnection = connection("conn-host");
        RecordingMatchStreamConnection guestConnection = connection("conn-guest");
        open(hostId, hostConnection);
        open(guestId, guestConnection);

        // when
        close(guestId, guestConnection);

        // then
        assertThat(matchStreamRegistry.find(new UserId(guestId))).isEmpty();
        assertThat(matchRoomMemberRegistry.usersIn(runningRoomId)).containsExactly(new UserId(hostId));
        assertThat(matchRoomMembership.isSubscribed(runningRoomId)).isTrue();

        // when -> 마지막 참가자가 닫으면 그 방 채널을 더 들을 이유가 없다
        close(hostId, hostConnection);

        // then
        assertThat(matchRoomMemberRegistry.usersIn(runningRoomId)).isEmpty();
        assertThat(matchRoomMembership.isSubscribed(runningRoomId)).isFalse();
    }

    private Long apply(UUID userId) {
        return applyMatchHandler.handle(
                new ApplyMatchCommand(userId, SLOT, TARGET_DISTANCE)).runningRoomId();
    }

    private void open(UUID userId, RecordingMatchStreamConnection connection) {
        openHandler.handle(new OpenMatchStreamCommand(userId, connection));
    }

    private void close(UUID userId, RecordingMatchStreamConnection connection) {
        closeHandler.handle(new CloseMatchStreamCommand(userId, connection));
    }

    private RecordingMatchStreamConnection connection(String id) {
        return new RecordingMatchStreamConnection(id);
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
}
