package com.runiverse.running_service.integration_test.match;

import com.github.f4b6a3.uuid.UuidCreator;
import com.runiverse.running_service.application.match.command.broadcast.BroadMatchEventHandler;
import com.runiverse.running_service.application.match.command.broadcast.BroadcastMatchEventCommand;
import com.runiverse.running_service.application.match.command.ready.NotifyRunningReadyHandler;
import com.runiverse.running_service.application.match.command.ready.RunningReadyExecutor;
import com.runiverse.running_service.application.match.common.MatchRoomChangedEvent;
import com.runiverse.running_service.application.match.port.out.MatchEventType;
import com.runiverse.running_service.application.match.port.out.MatchStreamEvent;
import com.runiverse.running_service.application.scheduling.command.recover.RecoverScheduledJobsHandler;
import com.runiverse.running_service.application.scheduling.command.run.RunScheduledJobCommand;
import com.runiverse.running_service.application.scheduling.command.run.RunScheduledJobHandler;
import com.runiverse.running_service.application.scheduling.command.schedule.ScheduleJobHandler;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.running.player.vo.RunningPlayerId;
import com.runiverse.running_service.domain.running.room.RunningRoom;
import com.runiverse.running_service.domain.running.room.SessionDraft;
import com.runiverse.running_service.domain.running.room.vo.RunningRoomStatus;
import com.runiverse.running_service.domain.running.room.vo.RunningRoomType;
import com.runiverse.running_service.domain.scheduling.ScheduledJob;
import com.runiverse.running_service.domain.scheduling.vo.ScheduledJobId;
import com.runiverse.running_service.domain.scheduling.vo.ScheduledJobType;
import com.runiverse.running_service.infrastructure.sse.MatchRoomMemberRegistry;
import com.runiverse.running_service.infrastructure.sse.MatchStreamRegistryAdapter;
import com.runiverse.running_service.integration_test.fake.InMemoryRunningStore;
import com.runiverse.running_service.integration_test.fake.InMemoryScheduledJobStore;
import com.runiverse.running_service.integration_test.fake.RecordingMatchStreamConnection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

// 예약 등록 → 발화 → 전파 → SSE 연결까지를 실제 클래스로 잇는다.
// Redis 왕복과 @TransactionalEventListener만 대신하고 나머지는 운영 코드 그대로다
@DisplayName("러닝 시작 통지 통합 테스트")
class RunningReadyIntegrationTest {

    private static final UUID USER_A = UuidCreator.getTimeOrderedEpoch();
    private static final UUID USER_B = UuidCreator.getTimeOrderedEpoch();
    private static final Duration READY_OFFSET = Duration.ofSeconds(10);
    private static final int AVG_PACE = 360;
    private static final int TARGET_DISTANCE = 5_000;

    private InMemoryRunningStore runningStore;
    private InMemoryScheduledJobStore scheduledJobStore;
    private MatchRoomMemberRegistry matchRoomMemberRegistry;
    private MatchStreamRegistryAdapter matchStreamRegistry;

    private ScheduleJobHandler scheduleJobHandler;
    private RunScheduledJobHandler runScheduledJobHandler;
    private RecoverScheduledJobsHandler recoverScheduledJobsHandler;

    private RecordingMatchStreamConnection connectionA;
    private RecordingMatchStreamConnection connectionB;

    @BeforeEach
    void setUp() {
        runningStore = new InMemoryRunningStore();
        scheduledJobStore = new InMemoryScheduledJobStore();
        matchRoomMemberRegistry = new MatchRoomMemberRegistry();
        matchStreamRegistry = new MatchStreamRegistryAdapter();

        BroadMatchEventHandler broadMatchEventHandler =
                new BroadMatchEventHandler(matchRoomMemberRegistry, matchStreamRegistry);
        // MatchEventDispatcher(AFTER_COMMIT) + Redis 왕복 + MatchEventListener를 대신한다 —
        // 발행된 이벤트가 곧바로 전파 단계로 들어간다
        ApplicationEventPublisher eventPublisher = event -> {
            if (event instanceof MatchRoomChangedEvent changed) {
                broadMatchEventHandler.handle(new BroadcastMatchEventCommand(changed.event()));
            }
        };

        RunningReadyExecutor runningReadyExecutor = new RunningReadyExecutor(
                new NotifyRunningReadyHandler(runningStore, eventPublisher));
        runScheduledJobHandler = new RunScheduledJobHandler(
                scheduledJobStore, scheduledJobStore, List.of(runningReadyExecutor));
        // 타이머 등록과 전파는 이 테스트의 주제가 아니다 — 발화는 아래에서 직접 부른다
        scheduleJobHandler = new ScheduleJobHandler(scheduledJobStore, event -> {
        });
        recoverScheduledJobsHandler = new RecoverScheduledJobsHandler(
                scheduledJobStore, job -> {
        }, runScheduledJobHandler);

        connectionA = new RecordingMatchStreamConnection("conn-a");
        connectionB = new RecordingMatchStreamConnection("conn-b");
    }

    @Nested
    @DisplayName("발화 테스트")
    class FireTest {

        @Test
        @DisplayName("예약이 발화하면 방 참가자 전원의 스트림으로 나간다")
        void notifiesEveryConnectedMember() {
            // given -> 시작 10초 전에 깨도록 예약해 둔 방. 둘 다 이 인스턴스에 붙어 있다
            long roomId = givenMatchedRoom(startAfter(READY_OFFSET));
            connect(USER_A, connectionA, roomId);
            connect(USER_B, connectionB, roomId);
            schedule(roomId, startAfter(READY_OFFSET).minus(READY_OFFSET));

            // when
            fire(roomId);

            // then
            assertThat(readyOf(connectionA).type()).isEqualTo(MatchEventType.RUNNING_READY);
            assertThat(readyOf(connectionB).type()).isEqualTo(MatchEventType.RUNNING_READY);
        }

        @Test
        @DisplayName("클라가 타이머를 걸 값이 함께 실린다")
        void carriesLaunchTimerValues() {
            // given
            LocalDateTime startAt = startAfter(READY_OFFSET);
            long roomId = givenMatchedRoom(startAt);
            connect(USER_A, connectionA, roomId);
            schedule(roomId, startAt.minus(READY_OFFSET));

            // when
            fire(roomId);

            // then -> 기기 시각을 믿지 않고 이 값으로 발사한다(api-spec 5-C)
            MatchStreamEvent event = readyOf(connectionA);
            assertThat(event.ready().runningRoomId()).isEqualTo(roomId);
            assertThat(event.ready().scheduledStartAt()).isEqualTo(startAt);
            assertThat(event.ready().startsInMs())
                    .isBetween(READY_OFFSET.toMillis() - 1_000, READY_OFFSET.toMillis());
        }

        @Test
        @DisplayName("스트림에 붙지 않은 참가자는 건너뛴다")
        void skipsMemberWithoutStream() {
            // given -> B는 다른 서버에 붙었거나 앱을 끈 상태다
            long roomId = givenMatchedRoom(startAfter(READY_OFFSET));
            connect(USER_A, connectionA, roomId);
            schedule(roomId, startAfter(READY_OFFSET).minus(READY_OFFSET));

            // when
            fire(roomId);

            // then -> 그쪽 인스턴스가 같은 메시지를 받아 자기 몫을 보낸다
            assertThat(connectionA.received()).hasSize(1);
            assertThat(connectionB.received()).isEmpty();
        }

        @Test
        @DisplayName("전원 취소로 닫힌 방에는 나가지 않는다")
        void skipsCancelledRoom() {
            // given -> 예약을 걸어둔 뒤 마지막 참가자가 나가 방이 닫혔다
            long roomId = givenRoom(RunningRoomStatus.CANCELLED, startAfter(READY_OFFSET));
            connect(USER_A, connectionA, roomId);
            schedule(roomId, startAfter(READY_OFFSET).minus(READY_OFFSET));

            // when
            fire(roomId);

            // then -> 알리면 클라가 홈으로 갔다가 러닝 화면으로 튄다
            assertThat(connectionA.received()).isEmpty();
        }

        @Test
        @DisplayName("두 인스턴스가 같은 예약을 깨도 한 번만 나간다")
        void firesOnlyOnce() {
            // given -> 예약은 여러 인스턴스가 타이머로 들고 있다
            long roomId = givenMatchedRoom(startAfter(READY_OFFSET));
            connect(USER_A, connectionA, roomId);
            schedule(roomId, startAfter(READY_OFFSET).minus(READY_OFFSET));

            // when
            fire(roomId);
            fire(roomId);

            // then -> is_sent 선점이 하나만 통과시킨다. 중복은 낭비가 아니라 이중화다
            assertThat(connectionA.received()).hasSize(1);
        }
    }

    @Nested
    @DisplayName("재시작 복구 테스트")
    class RecoveryTest {

        @Test
        @DisplayName("내려가 있는 동안 지나간 예약은 올라오자마자 발화한다")
        void firesOverdueJobOnBoot() {
            // given -> 발화 시각이 지난 채로 남아 있던 예약
            long roomId = givenMatchedRoom(LocalDateTime.now().plusSeconds(3));
            connect(USER_A, connectionA, roomId);
            schedule(roomId, LocalDateTime.now().minusSeconds(7));

            // when -> 부팅 시 미실행 예약을 훑는다
            recoverScheduledJobsHandler.recover();

            // then
            assertThat(readyOf(connectionA).type()).isEqualTo(MatchEventType.RUNNING_READY);
        }

        @Test
        @DisplayName("복구가 늦어 시작 시각을 넘겼으면 남은 시간은 0이다")
        void clampsToZeroWhenBootIsLate() {
            // given -> 서버가 오래 내려가 있어 시작 시각까지 지났다
            long roomId = givenMatchedRoom(LocalDateTime.now().minusMinutes(5));
            connect(USER_A, connectionA, roomId);
            schedule(roomId, LocalDateTime.now().minusMinutes(5).minus(READY_OFFSET));

            // when
            recoverScheduledJobsHandler.recover();

            // then -> 클라는 기다리지 않고 즉시 RUNNING_START를 쏜다
            assertThat(readyOf(connectionA).ready().startsInMs()).isZero();
        }

        @Test
        @DisplayName("이미 보낸 예약은 복구 대상이 아니다")
        void doesNotResendSentJob() {
            // given -> 한 번 나간 예약. is_sent가 찍혀 목록에서 빠진다
            long roomId = givenMatchedRoom(startAfter(READY_OFFSET));
            connect(USER_A, connectionA, roomId);
            schedule(roomId, LocalDateTime.now().minusSeconds(1));
            fire(roomId);

            // when -> 재부팅
            recoverScheduledJobsHandler.recover();

            // then -> 부팅할 때마다 옛 알림이 쏟아지면 안 된다
            assertThat(connectionA.received()).hasSize(1);
        }
    }

    private long givenMatchedRoom(LocalDateTime startAt) {
        return givenRoom(RunningRoomStatus.MATCHED, startAt);
    }

    private long givenRoom(RunningRoomStatus status, LocalDateTime startAt) {
        RunningRoom saved = runningStore.create(RunningRoom.builder()
                .type(RunningRoomType.MATCH)
                .status(status)
                // 종료 상태는 닫힌 시각이 있어야 복원된다
                .closeAt(status.isTerminal() ? LocalDateTime.now() : null)
                .startAt(startAt)
                .targetDistance(TARGET_DISTANCE)
                .avgPace(AVG_PACE)
                .currentPlayerCount(2)
                .maxPlayerCount(4)
                .sessions(List.of(
                        new SessionDraft(new UserId(USER_A), new RunningPlayerId(1L), 0, true),
                        new SessionDraft(new UserId(USER_B), new RunningPlayerId(2L), 0, true)))
                .build());
        return saved.getRunningRoomId().orElseThrow().value();
    }

    private void connect(UUID userId, RecordingMatchStreamConnection connection, long roomId) {
        matchStreamRegistry.register(new UserId(userId), connection);
        matchRoomMemberRegistry.join(new UserId(userId), roomId);
    }

    private void schedule(long roomId, LocalDateTime executeAt) {
        scheduleJobHandler.schedule(ScheduledJobType.RUNNING_READY, roomId, executeAt);
    }

    // 예약 실행기는 ID로 부른다 — 타이머가 들고 있던 값이다
    private void fire(long roomId) {
        ScheduledJob job = scheduledJobStore
                .findBy(ScheduledJobType.RUNNING_READY, roomId)
                .orElseThrow();
        runScheduledJobHandler.handle(new RunScheduledJobCommand(
                job.getScheduledJobId().map(ScheduledJobId::value).orElseThrow()));
    }

    private static MatchStreamEvent readyOf(RecordingMatchStreamConnection connection) {
        assertThat(connection.received()).hasSize(1);
        return connection.received().getFirst();
    }

    private static LocalDateTime startAfter(Duration untilStart) {
        return LocalDateTime.now().plus(untilStart);
    }
}
