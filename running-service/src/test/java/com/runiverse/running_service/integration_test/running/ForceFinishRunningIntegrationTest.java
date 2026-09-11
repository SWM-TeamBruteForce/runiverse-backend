package com.runiverse.running_service.integration_test.running;

import com.runiverse.running_service.application.auth.command.signup.SignUpCommand;
import com.runiverse.running_service.application.auth.command.signup.SignUpHandler;
import com.runiverse.running_service.application.match.common.MatchProperties;
import com.runiverse.running_service.application.running.command.finish.FinishRunningHandler;
import com.runiverse.running_service.application.running.command.finish.RunningFinishProperties;
import com.runiverse.running_service.application.running.command.forcefinish.ForceFinishRunningRoomHandler;
import com.runiverse.running_service.application.running.command.forcefinish.RunningForceFinishExecutor;
import com.runiverse.running_service.application.running.command.location.UpdateRunningLocationCommand;
import com.runiverse.running_service.application.running.command.location.UpdateRunningLocationHandler;
import com.runiverse.running_service.application.running.port.out.TrackPoint;
import com.runiverse.running_service.application.scheduling.command.run.RunScheduledJobCommand;
import com.runiverse.running_service.application.scheduling.command.run.RunScheduledJobHandler;
import com.runiverse.running_service.application.scheduling.command.schedule.ScheduleJobHandler;
import com.runiverse.running_service.application.user.command.onboarding.CompleteOnboardingCommand;
import com.runiverse.running_service.application.user.command.onboarding.CompleteOnboardingHandler;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.running.player.RunningPlayer;
import com.runiverse.running_service.domain.running.player.vo.RunningPlayerId;
import com.runiverse.running_service.domain.running.player.vo.RunningPlayerStatus;
import com.runiverse.running_service.domain.running.room.RoomSession;
import com.runiverse.running_service.domain.running.room.RunningRoom;
import com.runiverse.running_service.domain.running.room.SessionDraft;
import com.runiverse.running_service.domain.running.room.vo.RunningRoomStatus;
import com.runiverse.running_service.domain.running.room.vo.RunningRoomType;
import com.runiverse.running_service.domain.scheduling.ScheduledJob;
import com.runiverse.running_service.domain.scheduling.vo.ScheduledJobId;
import com.runiverse.running_service.domain.scheduling.vo.ScheduledJobType;
import com.runiverse.running_service.integration_test.IntegrationTestSupport;
import com.runiverse.running_service.integration_test.fake.InMemoryScheduledJobStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

// 예약 발화 → 남은 참가자 확정 → 방 종료까지를 실제 클래스로 잇는다.
// 강제 종료와 러닝 종료 두 핸들러가 같은 방을 놓고 맞물리는 구간이라
// 단위 테스트(러닝 종료를 목으로 막는다)로는 드러나지 않는 것들이 여기서 걸린다
@DisplayName("러닝 강제 종료 통합 테스트")
public class ForceFinishRunningIntegrationTest extends IntegrationTestSupport {

    private static final String PASSWORD = "Password123!";
    private static final int AVG_PACE = 330;                     // 5분 30초/km
    private static final int TARGET_DISTANCE = 5_000;
    private static final BigDecimal WEIGHT = new BigDecimal("70.0");
    private static final LocalDateTime TRACK_START = LocalDateTime.of(2026, 9, 8, 19, 0, 0);
    // TrackDistance와 같은 지구 반경 — 어긋나면 의도한 거리와 측정 거리가 벌어진다
    private static final double METERS_PER_DEGREE = Math.toRadians(1) * 6_371_008.8;
    // 초당 2.5m라 목표(5km)를 확실히 넘긴다
    private static final int COMPLETING_POINTS = 2_101;

    // 운영 설정과 같은 값
    private static final Duration FORCE_FINISH_OFFSET = Duration.ofHours(6);
    private static final Duration MATCH_COOLDOWN = Duration.ofMinutes(20);
    private static final Duration RUNNING_COOLDOWN = Duration.ofMinutes(20);
    private static final MatchProperties MATCH_PROPERTIES = new MatchProperties(
            Duration.ofMinutes(10), Duration.ofSeconds(10), FORCE_FINISH_OFFSET,
            10, MATCH_COOLDOWN);
    private static final RunningFinishProperties FINISH_PROPERTIES = new RunningFinishProperties(
            0.8, 10, 100, 60, 3.0, RUNNING_COOLDOWN);

    private InMemoryScheduledJobStore scheduledJobStore;
    private SignUpHandler signUpHandler;
    private CompleteOnboardingHandler completeOnboardingHandler;
    private UpdateRunningLocationHandler updateRunningLocationHandler;
    private ScheduleJobHandler scheduleJobHandler;
    private RunScheduledJobHandler runScheduledJobHandler;
    // 제재가 실제로 걸렸는지 보려면 발급 자체를 잡아야 한다 — 근거(status)와 별개 축이다
    private Map<UUID, Duration> cooldowns;

    @BeforeEach
    void setUp() {
        scheduledJobStore = new InMemoryScheduledJobStore();
        cooldowns = new HashMap<>();
        signUpHandler = newSignUpHandler();
        completeOnboardingHandler = new CompleteOnboardingHandler(
                userStore,        // LoadUserByIdPort
                onboardingStore,  // ExistsOnboardingPort
                onboardingStore,  // CheckNicknameDuplicatePort
                onboardingStore   // SaveOnboardingPort
        );
        updateRunningLocationHandler = new UpdateRunningLocationHandler(
                runningTrackStore,       // AppendRunningTrackPort
                runningDistanceStore,    // LoadRunningDistancePort
                runningDistanceStore,    // SaveRunningDistancePort
                runningProgressPublisher, // PublishRunningProgressPort
                newUpdateRunningComboJudge()
        );
        FinishRunningHandler finishRunningHandler = new FinishRunningHandler(
                runningStore,       // LoadRunningRoomPort
                runningStore,       // LoadRoomPlayerPort
                runningTrackStore,  // LoadRunningTrackPort
                onboardingStore,    // LoadUserWeightPort
                weatherProvider,    // LoadWeatherPort
                gpsTrackUploader,   // SaveGpsTrackPort
                runningRecordStore, // CreateRunningRecordPort
                runningStore,       // UpdateRunningPlayerPort
                runningTrackStore,  // DeleteRunningTrackPort
                runningStore,       // ExistsRunningPlayerPort
                runningStore,       // UpdateRunningRoomPort
                this::recordCooldown, // StartMatchCooldownPort
                runningRecordStore, // ExistsRunningRecordPort
                FINISH_PROPERTIES
        );
        ForceFinishRunningRoomHandler forceFinishRunningRoomHandler =
                new ForceFinishRunningRoomHandler(
                        runningStore,         // LockRunningRoomPort
                        runningStore,         // LoadRunningRoomPort
                        runningStore,         // LoadRoomPlayerPort
                        runningStore,         // UpdateRunningRoomPort
                        runningStore,         // UpdateRunningPlayerPort
                        this::recordCooldown, // StartMatchCooldownPort
                        runningRecordStore,   // ExistsRunningRecordPort
                        finishRunningHandler, // FinishRunningUsecase
                        MATCH_PROPERTIES
                );
        runScheduledJobHandler = new RunScheduledJobHandler(
                scheduledJobStore, scheduledJobStore,
                List.of(new RunningForceFinishExecutor(forceFinishRunningRoomHandler)));
        // 타이머 등록과 전파는 이 테스트의 주제가 아니다 — 발화는 아래에서 직접 부른다
        scheduleJobHandler = new ScheduleJobHandler(scheduledJobStore, event -> {
        });
    }

    @Test
    @DisplayName("뛴 사람은 러닝 종료로, 안 나타난 사람은 확정 후 이탈로 함께 닫는다")
    void closesRunnerAndNoShowInOneRoom() {
        // given -> 2인 확정 방에서 A만 붙어 목표를 채웠고 B는 한 번도 오지 않았다
        UUID runner = onboardedUser("runner@runiverse.com", "러너킴");
        UUID noShow = onboardedUser("noshow@runiverse.com", "안온사람");
        long roomId = givenStartedMatchRoom(runner, noShow);
        runFor(runner, roomId, COMPLETING_POINTS);
        schedule(roomId);

        // when
        fire(roomId);

        // then -> 뛴 사람은 평소 종료와 똑같다. 기록도 남는다
        assertThat(storedPlayer(roomId, runner).getStatus())
                .isEqualTo(RunningPlayerStatus.COMPLETED);
        assertThat(runningRecordStore.find(roomId, new UserId(runner))).isPresent();
        // 안 나타난 사람은 RUNNING을 거치지 않았으므로 조기 종료가 아니라 확정 후 이탈이다
        assertThat(storedPlayer(roomId, noShow).getStatus())
                .isEqualTo(RunningPlayerStatus.MATCHED_LEFT_PENALTY);
        assertThat(runningRecordStore.find(roomId, new UserId(noShow))).isEmpty();
    }

    @Test
    @DisplayName("제재는 안 나타난 사람에게만, 이탈 쪽 쿨다운으로 걸린다")
    void penalizesOnlyNoShowWithMatchCooldown() {
        // given -> 목표를 채운 A는 제재 대상이 아니다
        UUID runner = onboardedUser("runner@runiverse.com", "러너킴");
        UUID noShow = onboardedUser("noshow@runiverse.com", "안온사람");
        long roomId = givenStartedMatchRoom(runner, noShow);
        runFor(runner, roomId, COMPLETING_POINTS);
        schedule(roomId);

        // when
        fire(roomId);

        // then -> 남는 상태가 MATCHED_LEFT_*라 조기 종료(running-finish)가 아니라 이탈(match) 값이다
        assertThat(cooldowns).containsOnlyKeys(noShow);
        assertThat(cooldowns.get(noShow)).isEqualTo(MATCH_COOLDOWN);
    }

    @Test
    @DisplayName("뛴 사람이 있으면 방은 완료로 닫히고 자리는 전부 비워진다")
    void closesRoomAsFinishedWhenSomeoneRan() {
        // given
        UUID runner = onboardedUser("runner@runiverse.com", "러너킴");
        UUID noShow = onboardedUser("noshow@runiverse.com", "안온사람");
        long roomId = givenStartedMatchRoom(runner, noShow);
        runFor(runner, roomId, COMPLETING_POINTS);
        schedule(roomId);

        // when
        fire(roomId);

        // then -> 남길 기록이 있으니 완료다. 강제 종료가 들고 있던 방 인스턴스로 덮어썼다면
        //         러닝 종료가 만든 이 전이가 사라진다
        RunningRoom room = storedRoom(roomId);
        assertThat(room.getStatus()).isEqualTo(RunningRoomStatus.FINISHED);
        assertThat(room.getCloseAt()).isPresent();
        // 인원은 확정 시점 값으로 고정된다 — 자리를 비우는 것과 인원을 줄이는 것은 다르다
        assertThat(room.getPlayerCount().current()).isEqualTo(2);
        assertThat(room.getSessions()).extracting(RoomSession::isConnected)
                .containsOnly(false);
        assertThat(runningTrackStore.isEmpty(roomId, new UserId(runner))).isTrue();
    }

    @Test
    @DisplayName("아무도 나타나지 않은 방은 남길 기록이 없어 취소로 닫힌다")
    void cancelsRoomNobodyRanIn() {
        // given -> 둘 다 채널에 붙지 않았다. 방을 닫아 줄 러닝 종료가 아예 없다
        UUID first = onboardedUser("first@runiverse.com", "안온사람1");
        UUID second = onboardedUser("second@runiverse.com", "안온사람2");
        long roomId = givenStartedMatchRoom(first, second);
        schedule(roomId);

        // when
        fire(roomId);

        // then
        RunningRoom room = storedRoom(roomId);
        assertThat(room.getStatus()).isEqualTo(RunningRoomStatus.CANCELLED);
        assertThat(room.getCloseAt()).isPresent();
        assertThat(runningRecordStore.size()).isZero();
        assertThat(storedPlayer(roomId, first).getStatus())
                .isEqualTo(RunningPlayerStatus.MATCHED_LEFT_PENALTY);
        assertThat(storedPlayer(roomId, second).getStatus())
                .isEqualTo(RunningPlayerStatus.MATCHED_LEFT_PENALTY);
    }

    @Test
    @DisplayName("1인 확정 방의 미출석은 제재 없이 닫는다")
    void doesNotPenalizeNoShowInSinglePlayerRoom() {
        // given -> 안 나타나도 곤란해지는 상대가 없다. 시작 전 이탈 면제와 같은 기준이다
        UUID alone = onboardedUser("alone@runiverse.com", "혼자온사람");
        long roomId = givenStartedMatchRoom(alone);
        schedule(roomId);

        // when
        fire(roomId);

        // then
        assertThat(storedPlayer(roomId, alone).getStatus())
                .isEqualTo(RunningPlayerStatus.MATCHED_LEFT_NO_PENALTY);
        assertThat(cooldowns).isEmpty();
        assertThat(storedRoom(roomId).getStatus()).isEqualTo(RunningRoomStatus.CANCELLED);
    }

    @Test
    @DisplayName("강제 종료가 끝나면 활성 신청이 남지 않는다")
    void leavesNoActiveApplication() {
        // given -> 방이 열린 채로 남으면 신청도 활성으로 남아 다음 러닝이 영영 막힌다.
        //          이 예약을 만든 이유 자체다
        UUID runner = onboardedUser("runner@runiverse.com", "러너킴");
        UUID noShow = onboardedUser("noshow@runiverse.com", "안온사람");
        long roomId = givenStartedMatchRoom(runner, noShow);
        runFor(runner, roomId, COMPLETING_POINTS);
        schedule(roomId);

        // when
        fire(roomId);

        // then -> 뛴 사람도 안 나타난 사람도 신청이 끝나 있다
        assertThat(runningStore.existsActive(new UserId(runner))).isFalse();
        assertThat(runningStore.existsActive(new UserId(noShow))).isFalse();
    }

    @Test
    @DisplayName("예약이 두 번 깨도 한 번만 실행된다")
    void firesOnlyOnce() {
        // given -> 인스턴스 여럿이 같은 예약을 타이머로 들고 있다.
        //          두 번째가 그대로 지나가면 이미 닫힌 참가자에 다시 손을 대 도메인 예외가 난다
        UUID runner = onboardedUser("runner@runiverse.com", "러너킴");
        UUID noShow = onboardedUser("noshow@runiverse.com", "안온사람");
        long roomId = givenStartedMatchRoom(runner, noShow);
        runFor(runner, roomId, COMPLETING_POINTS);
        schedule(roomId);

        // when
        fire(roomId);
        fire(roomId);

        // then -> 기록이 두 번 쌓이지 않고 상태도 그대로다
        assertThat(runningRecordStore.size()).isOne();
        assertThat(storedRoom(roomId).getStatus()).isEqualTo(RunningRoomStatus.FINISHED);
        assertThat(cooldowns).containsOnlyKeys(noShow);
    }

    private void recordCooldown(UserId userId, Duration cooldown) {
        cooldowns.put(userId.value(), cooldown);
    }

    private UUID onboardedUser(String email, String nickname) {
        UUID userId = signUpHandler.handle(
                new SignUpCommand(issueVerificationTicket(email), PASSWORD)).userId();
        completeOnboardingHandler.handle(new CompleteOnboardingCommand(
                userId, nickname, "MALE", LocalDate.of(1998, 5, 20),
                AVG_PACE, WEIGHT, new BigDecimal("175.0")));
        return userId;
    }

    // 강제 종료가 도는 시점의 방 — 시작 스케줄러가 이미 STARTED로 올려두었고
    // 시작 시각은 유예만큼 지나 있다. 붙은 사람만 RUNNING이다
    private long givenStartedMatchRoom(UUID... members) {
        LocalDateTime startAt = LocalDateTime.now().minus(FORCE_FINISH_OFFSET);
        List<SessionDraft> sessions = new ArrayList<>();
        for (UUID member : members) {
            RunningPlayer saved = runningStore.create(RunningPlayer.builder()
                    .userId(member)
                    .status(RunningPlayerStatus.JOINED)
                    .avgPace(AVG_PACE)
                    .targetDistance(TARGET_DISTANCE)
                    .startAt(startAt)
                    .build());
            sessions.add(new SessionDraft(new UserId(member),
                    saved.getRunningPlayerId().orElseThrow(), 0, true));
        }
        RunningRoom saved = runningStore.create(RunningRoom.builder()
                .type(RunningRoomType.MATCH)
                .status(RunningRoomStatus.STARTED)
                .startAt(startAt)
                .targetDistance(TARGET_DISTANCE)
                .avgPace(AVG_PACE)
                .currentPlayerCount(members.length)
                .maxPlayerCount(4)
                .sessions(sessions)
                .build());
        return saved.getRunningRoomId().orElseThrow().value();
    }

    // 채널에 붙어 좌표를 밀어 넣은 사람 = RUNNING. 안 부르면 JOINED로 남아 미출석이 된다
    private void runFor(UUID userId, long roomId, int count) {
        RunningPlayer player = storedPlayer(roomId, userId);
        player.start();
        runningStore.update(player);
        List<TrackPoint> points = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            points.add(new TrackPoint(i, 37.5 + i * 2.5 / METERS_PER_DEGREE, 127.0,
                    null, 5.0, null, null, 168, null, TRACK_START.plusSeconds(i)));
        }
        updateRunningLocationHandler.handle(
                new UpdateRunningLocationCommand(userId, roomId, TARGET_DISTANCE, points));
    }

    private void schedule(long roomId) {
        scheduleJobHandler.schedule(ScheduledJobType.RUNNING_FORCE_FINISH, roomId,
                storedRoom(roomId).getStartAt().plus(FORCE_FINISH_OFFSET));
    }

    // 예약 실행기는 ID로 부른다 — 타이머가 들고 있던 값이다
    private void fire(long roomId) {
        ScheduledJob job = scheduledJobStore
                .findBy(ScheduledJobType.RUNNING_FORCE_FINISH, roomId)
                .orElseThrow();
        runScheduledJobHandler.handle(new RunScheduledJobCommand(
                job.getScheduledJobId().map(ScheduledJobId::value).orElseThrow()));
    }

    private RunningRoom storedRoom(long roomId) {
        return runningStore.findRoom(roomId).orElseThrow();
    }

    private RunningPlayer storedPlayer(long roomId, UUID userId) {
        RunningPlayerId playerId = storedRoom(roomId).getSessions().stream()
                .filter(session -> session.isSameUser(new UserId(userId)))
                .findFirst()
                .orElseThrow()
                .getRunningPlayerId();
        return runningStore.findPlayer(playerId.value()).orElseThrow();
    }
}
