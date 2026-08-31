package com.runiverse.running_service.integration_test.running;

import com.runiverse.running_service.application.auth.command.signup.SignUpCommand;
import com.runiverse.running_service.application.auth.command.signup.SignUpHandler;
import com.runiverse.running_service.application.running.command.finish.FinishRunningCommand;
import com.runiverse.running_service.application.running.command.finish.FinishRunningHandler;
import com.runiverse.running_service.application.running.command.finish.RunningFinishProperties;
import com.runiverse.running_service.application.running.command.location.UpdateRunningLocationCommand;
import com.runiverse.running_service.application.running.command.location.UpdateRunningLocationHandler;
import com.runiverse.running_service.application.running.command.solo.OpenSoloRoomCommand;
import com.runiverse.running_service.application.running.command.solo.OpenSoloRoomHandler;
import com.runiverse.running_service.application.running.command.start.StartRunningCommand;
import com.runiverse.running_service.application.running.command.start.StartRunningHandler;
import com.runiverse.running_service.application.running.exception.NotRoomPlayerException;
import com.runiverse.running_service.application.running.port.out.TrackPoint;
import com.runiverse.running_service.application.user.command.onboarding.CompleteOnboardingCommand;
import com.runiverse.running_service.application.user.command.onboarding.CompleteOnboardingHandler;
import com.runiverse.running_service.domain.common.vo.UserId;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import com.runiverse.running_service.domain.running.player.RunningPlayer;
import com.runiverse.running_service.domain.running.player.vo.RunningPlayerId;
import com.runiverse.running_service.domain.running.player.vo.RunningPlayerStatus;
import com.runiverse.running_service.domain.running.record.RunningRecord;
import com.runiverse.running_service.domain.running.room.RunningRoom;
import com.runiverse.running_service.domain.running.room.vo.RunningRoomStatus;
import com.runiverse.running_service.integration_test.IntegrationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("러닝 종료 통합 테스트")
public class FinishRunningIntegrationTest extends IntegrationTestSupport {

    private static final String PASSWORD = "Password123!";
    private static final String EMAIL = "runner@runiverse.com";
    private static final String NICKNAME = "러너킴";
    private static final int AVG_PACE = 330;                     // 5분 30초/km
    private static final BigDecimal WEIGHT = new BigDecimal("70.0");
    private static final LocalDateTime TRACK_START = LocalDateTime.of(2026, 8, 27, 19, 0, 0);
    // TrackDistance와 같은 지구 반경 — 어긋나면 의도한 거리와 측정 거리가 벌어진다
    private static final double METERS_PER_DEGREE = Math.toRadians(1) * 6_371_008.8;

    // 운영 설정과 같은 값
    private static final RunningFinishProperties PROPERTIES = new RunningFinishProperties(
            0.8, 10, 100, 60, 3.0);

    private SignUpHandler signUpHandler;
    private CompleteOnboardingHandler completeOnboardingHandler;
    private OpenSoloRoomHandler openSoloRoomHandler;
    private StartRunningHandler startRunningHandler;
    private UpdateRunningLocationHandler updateRunningLocationHandler;
    private FinishRunningHandler handler;

    @BeforeEach
    void setUp() {
        signUpHandler = newSignUpHandler();
        completeOnboardingHandler = new CompleteOnboardingHandler(
                userStore,        // LoadUserByIdPort
                onboardingStore,  // ExistsOnboardingPort
                onboardingStore,  // CheckNicknameDuplicatePort
                onboardingStore   // SaveOnboardingPort
        );
        openSoloRoomHandler = new OpenSoloRoomHandler(
                runningStore,     // ExistsActiveRunningPlayerPort
                onboardingStore,  // LoadUserAvgPacePort
                runningStore,     // CreateRunningPlayerPort
                runningStore      // CreateRunningRoomPort
        );
        startRunningHandler = new StartRunningHandler(
                runningStore,     // LoadRunningRoomPort
                runningStore,     // UpdateRunningRoomPort
                runningStore,     // LoadActiveRunningPlayerPort
                runningStore      // UpdateRunningPlayerPort
        );
        updateRunningLocationHandler = new UpdateRunningLocationHandler(
                runningTrackStore,     // AppendRunningTrackPort
                runningDistanceStore,  // LoadRunningDistancePort
                runningDistanceStore,  // SaveRunningDistancePort
                runningProgressPublisher // PublishRunningProgressPort
        );
        handler = new FinishRunningHandler(
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
                PROPERTIES
        );
    }

    private UUID onboardedUser(String email, String nickname) {
        UUID userId = signUpHandler.handle(
                new SignUpCommand(issueVerificationTicket(email), PASSWORD)).userId();
        completeOnboardingHandler.handle(new CompleteOnboardingCommand(
                userId, nickname, "MALE", LocalDate.of(1998, 5, 20),
                AVG_PACE, WEIGHT, new BigDecimal("175.0")));
        return userId;
    }

    // 솔로 개시 → RUNNING_START까지. 여기까지가 RUNNING_FINISH의 전제다
    private Long runningRoom(UUID userId) {
        Long runningRoomId = openSoloRoomHandler.handle(
                new OpenSoloRoomCommand(userId)).runningRoomId();
        startRunningHandler.handle(new StartRunningCommand(userId, runningRoomId));
        return runningRoomId;
    }

    // 북쪽으로 초당 2.5m씩 달린 좌표를 실제 경로(WS 좌표 배치)로 밀어 넣는다
    private void runFor(UUID userId, Long runningRoomId, int count) {
        List<TrackPoint> points = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            points.add(new TrackPoint(i, 37.5 + i * 2.5 / METERS_PER_DEGREE, 127.0,
                    null, 5.0, null, null, 168, null, TRACK_START.plusSeconds(i)));
        }
        // 솔로 방은 목표 거리가 없다
        updateRunningLocationHandler.handle(
                new UpdateRunningLocationCommand(userId, runningRoomId, null, points));
    }

    // 센서 이상값 재현용 — 좌표·정확도는 정상이고 케이던스·고도·시각만 주입한다
    private static TrackPoint sensorPoint(int i, Integer cadenceSpm, Double altitudeMeters,
                                          LocalDateTime recordedAt) {
        return new TrackPoint(i, 37.5 + i * 2.5 / METERS_PER_DEGREE, 127.0,
                altitudeMeters, 5.0, null, null, cadenceSpm, null, recordedAt);
    }

    private void runWith(UUID userId, Long runningRoomId, List<TrackPoint> points) {
        updateRunningLocationHandler.handle(
                new UpdateRunningLocationCommand(userId, runningRoomId, null, points));
    }

    private void finish(UUID userId, Long runningRoomId) {
        handler.handle(new FinishRunningCommand(runningRoomId, userId, false));
    }

    private RunningRoom storedRoom(Long runningRoomId) {
        return runningStore.findRoom(runningRoomId).orElseThrow();
    }

    private RunningPlayer storedPlayer(Long runningRoomId) {
        RunningPlayerId playerId = storedRoom(runningRoomId).getSessions().get(0)
                .getRunningPlayerId();
        return runningStore.findPlayer(playerId.value()).orElseThrow();
    }

    @Test
    @DisplayName("솔로 러닝을 끝내면 참가자·방·기록이 한 번에 확정된다")
    void finishesSoloRunning() {
        // given -> 약 1,000m를 뛰었다
        UUID userId = onboardedUser(EMAIL, NICKNAME);
        Long runningRoomId = runningRoom(userId);
        runFor(userId, runningRoomId, 400);

        // when
        finish(userId, runningRoomId);

        // then -> 목표가 없는 솔로는 사용자가 끝낸 것이 곧 완주다
        assertThat(storedPlayer(runningRoomId).getStatus())
                .isEqualTo(RunningPlayerStatus.COMPLETED);
        assertThat(storedPlayer(runningRoomId).getDeletedAt()).isPresent();
        // 혼자 뛰었어도 CANCELLED가 아니라 FINISHED다
        assertThat(storedRoom(runningRoomId).getStatus()).isEqualTo(RunningRoomStatus.FINISHED);
        assertThat(runningRecordStore.size()).isEqualTo(1);
    }

    @Test
    @DisplayName("확정한 지표가 기록에 그대로 남는다")
    void savesRecordMetrics() {
        // given
        UUID userId = onboardedUser(EMAIL, NICKNAME);
        Long runningRoomId = runningRoom(userId);
        runFor(userId, runningRoomId, 400);

        // when
        finish(userId, runningRoomId);

        // then -> 구간 경계가 10m라 997.5m는 990m에서 끊긴다
        RunningRecord record = runningRecordStore.find(runningRoomId, new UserId(userId))
                .orElseThrow();
        assertThat(record.getTotalDistance().meters()).isEqualTo(990);
        assertThat(record.getSplits()).hasSize(99);
        assertThat(record.getRoutePolyline().value()).isNotBlank();
        assertThat(record.getGpsTrackKey().value())
                .isEqualTo("gps-tracks/%s/%d/2026-08-27.json".formatted(userId, runningRoomId));
        assertThat(record.getWeatherCode().value()).isZero();
        assertThat(record.getAvgCadence().orElseThrow().stepsPerMinute()).isEqualTo(168);
    }

    @Test
    @DisplayName("케이던스 오전송이 섞여도 정상 표본으로 기록을 만든다")
    void ignoresCadenceOutliers() {
        // given -> 누적 걸음수(8,500)가 케이던스 필드로 온다 — 안드로이드 오전송의 전형
        UUID userId = onboardedUser(EMAIL, NICKNAME);
        Long runningRoomId = runningRoom(userId);
        List<TrackPoint> points = new ArrayList<>();
        for (int i = 0; i < 400; i++) {
            points.add(sensorPoint(i, i % 100 == 0 ? 8_500 : 168, null, TRACK_START.plusSeconds(i)));
        }
        runWith(userId, runningRoomId, points);

        // when
        finish(userId, runningRoomId);

        // then -> 값 하나 때문에 기록이 사라지면 안 된다 — 케이던스는 정상 표본의 평균이다
        RunningRecord record = runningRecordStore.find(runningRoomId, new UserId(userId))
                .orElseThrow();
        assertThat(record.getAvgCadence().orElseThrow().stepsPerMinute()).isEqualTo(168);
    }

    @Test
    @DisplayName("케이던스 표본이 전부 범위 밖이면 케이던스만 비운다")
    void dropsCadenceWhenEverySampleIsOutOfRange() {
        // given
        UUID userId = onboardedUser(EMAIL, NICKNAME);
        Long runningRoomId = runningRoom(userId);
        List<TrackPoint> points = new ArrayList<>();
        for (int i = 0; i < 400; i++) {
            points.add(sensorPoint(i, 8_500, null, TRACK_START.plusSeconds(i)));
        }
        runWith(userId, runningRoomId, points);

        // when
        finish(userId, runningRoomId);

        // then -> 거리·시간·경로는 산다
        RunningRecord record = runningRecordStore.find(runningRoomId, new UserId(userId))
                .orElseThrow();
        assertThat(record.getAvgCadence()).isEmpty();
        assertThat(record.getTotalDistance().meters()).isEqualTo(990);
    }

    @Test
    @DisplayName("고도 글리치는 고도 지표만 비우고 기록은 만든다")
    void dropsElevationOnGlitch() {
        // given -> 기압계 튐 — 한 좌표만 고도 10,000,000m
        UUID userId = onboardedUser(EMAIL, NICKNAME);
        Long runningRoomId = runningRoom(userId);
        List<TrackPoint> points = new ArrayList<>();
        for (int i = 0; i < 400; i++) {
            points.add(sensorPoint(i, 168, i == 200 ? 10_000_000.0 : 10.0,
                    TRACK_START.plusSeconds(i)));
        }
        runWith(userId, runningRoomId, points);

        // when
        finish(userId, runningRoomId);

        // then
        RunningRecord record = runningRecordStore.find(runningRoomId, new UserId(userId))
                .orElseThrow();
        assertThat(record.getTotalElevationGain()).isEmpty();
        assertThat(record.getTotalDistance().meters()).isEqualTo(990);
    }

    @Test
    @DisplayName("int 범위를 넘는 고도 글리치도 래핑되지 않고 비워진다")
    void dropsElevationOnIntOverflowGlitch() {
        // given -> 2^32 + 정상값. int로 먼저 자르는 잘못된 구현은 0으로 래핑돼 검증을 통과해버린다
        UUID userId = onboardedUser(EMAIL, NICKNAME);
        Long runningRoomId = runningRoom(userId);
        List<TrackPoint> points = new ArrayList<>();
        for (int i = 0; i < 400; i++) {
            points.add(sensorPoint(i, 168, i == 200 ? 4_294_967_306.0 : 10.0,
                    TRACK_START.plusSeconds(i)));
        }
        runWith(userId, runningRoomId, points);

        // when
        finish(userId, runningRoomId);

        // then
        RunningRecord record = runningRecordStore.find(runningRoomId, new UserId(userId))
                .orElseThrow();
        assertThat(record.getTotalElevationGain()).isEmpty();
    }

    @Test
    @DisplayName("시계가 미래로 튄 트랙은 기록 없이 상태만 확정한다")
    void confirmsStatusWhenClockJumps() {
        // given -> 뒤쪽 좌표의 시각이 이틀 뒤다 — 단말 재부팅·시간대 변경의 전형.
        // 경계 시각은 경계를 감싸는 두 점만 표본하므로, 경계(980m) 위의 점(392)에서 튀게 한다 —
        // 표본되지 않는 점의 점프는 수학에 안 실려 기록이 정상으로 만들어지는 게 맞다
        UUID userId = onboardedUser(EMAIL, NICKNAME);
        Long runningRoomId = runningRoom(userId);
        List<TrackPoint> points = new ArrayList<>();
        for (int i = 0; i < 400; i++) {
            points.add(sensorPoint(i, 168, null,
                    i == 392 ? TRACK_START.plusDays(2) : TRACK_START.plusSeconds(i)));
        }
        runWith(userId, runningRoomId, points);

        // when -> 예외가 새면 세션이 죽고 6시간 동안 종료가 안 된다
        finish(userId, runningRoomId);

        // then
        assertThat(runningRecordStore.size()).isZero();
        assertThat(storedPlayer(runningRoomId).getStatus())
                .isEqualTo(RunningPlayerStatus.COMPLETED);
    }

    @Test
    @DisplayName("중간만 미래로 튀었다 돌아온 트랙도 기록 없이 상태만 확정한다")
    void confirmsStatusWhenClockJumpsMidway() {
        // given -> 단조화가 중간의 튐을 이후 전 구간에 보존한다 — 끝 시각만 보는 잘못된 검사가 놓치는 모양
        UUID userId = onboardedUser(EMAIL, NICKNAME);
        Long runningRoomId = runningRoom(userId);
        List<TrackPoint> points = new ArrayList<>();
        for (int i = 0; i < 400; i++) {
            points.add(sensorPoint(i, 168, null,
                    i == 200 ? TRACK_START.plusDays(2) : TRACK_START.plusSeconds(i)));
        }
        runWith(userId, runningRoomId, points);

        // when
        finish(userId, runningRoomId);

        // then
        assertThat(runningRecordStore.size()).isZero();
        assertThat(storedPlayer(runningRoomId).getStatus())
                .isEqualTo(RunningPlayerStatus.COMPLETED);
    }

    @Test
    @DisplayName("종료하면 원본 트랙을 올리고 Redis 버퍼를 비운다")
    void uploadsTrackAndClearsBuffer() {
        // given
        UUID userId = onboardedUser(EMAIL, NICKNAME);
        Long runningRoomId = runningRoom(userId);
        runFor(userId, runningRoomId, 400);

        // when
        finish(userId, runningRoomId);

        // then -> 버퍼가 남으면 재연결 재전송이 끝난 러닝에 다시 쌓인다
        assertThat(gpsTrackUploader.uploads()).hasSize(1);
        assertThat(gpsTrackUploader.uploads().get(0).raw()).startsWith("[[0,");
        assertThat(runningTrackStore.isEmpty(runningRoomId, new UserId(userId))).isTrue();
    }

    @Test
    @DisplayName("트랜잭션 동기화가 활성이면 트랙 삭제를 커밋 뒤로 미룬다")
    void deletesTrackOnlyAfterCommit() {
        // given
        UUID userId = onboardedUser(EMAIL, NICKNAME);
        Long runningRoomId = runningRoom(userId);
        runFor(userId, runningRoomId, 400);
        // 페이크 조립엔 트랜잭션이 없다 — 동기화만 수동으로 켜서 커밋 경계를 흉내 낸다
        TransactionSynchronizationManager.initSynchronization();
        try {
            // when
            finish(userId, runningRoomId);

            // then -> 커밋 전에 지우면 커밋 실패 시 재시도가 빈 트랙으로 0m 확정한다
            assertThat(runningTrackStore.isEmpty(runningRoomId, new UserId(userId))).isFalse();

            // when -> 커밋 성공을 흉내 낸다
            TransactionSynchronizationManager.getSynchronizations()
                    .forEach(TransactionSynchronization::afterCommit);

            // then
            assertThat(runningTrackStore.isEmpty(runningRoomId, new UserId(userId))).isTrue();
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    @DisplayName("롤백되면 트랙을 지우지 않는다 — 재시도가 같은 트랙으로 다시 확정한다")
    void keepsTrackOnRollback() {
        // given
        UUID userId = onboardedUser(EMAIL, NICKNAME);
        Long runningRoomId = runningRoom(userId);
        runFor(userId, runningRoomId, 400);
        TransactionSynchronizationManager.initSynchronization();
        try {
            finish(userId, runningRoomId);

            // when -> 커밋 실패를 흉내 낸다
            TransactionSynchronizationManager.getSynchronizations().forEach(synchronization ->
                    synchronization.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));

            // then
            assertThat(runningTrackStore.isEmpty(runningRoomId, new UserId(userId))).isFalse();
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    @DisplayName("ack를 놓친 클라가 다시 보내도 기록은 하나뿐이다")
    void isIdempotent() {
        // given
        UUID userId = onboardedUser(EMAIL, NICKNAME);
        Long runningRoomId = runningRoom(userId);
        runFor(userId, runningRoomId, 400);
        finish(userId, runningRoomId);

        // when -> 재전송. 저장소가 UNIQUE(room, user)를 강제하므로 덮어쓰면 여기서 터진다
        finish(userId, runningRoomId);

        // then
        assertThat(runningRecordStore.size()).isEqualTo(1);
        assertThat(storedPlayer(runningRoomId).getStatus())
                .isEqualTo(RunningPlayerStatus.COMPLETED);
    }

    @Test
    @DisplayName("좌표가 하나도 없으면 기록 없이 상태만 확정한다")
    void confirmsStatusWithoutRecord() {
        // given -> 시작하자마자 끊긴 러닝
        UUID userId = onboardedUser(EMAIL, NICKNAME);
        Long runningRoomId = runningRoom(userId);

        // when
        finish(userId, runningRoomId);

        // then -> 솔로는 목표가 없어 거리 0이어도 완주로 확정된다
        assertThat(runningRecordStore.size()).isZero();
        assertThat(gpsTrackUploader.isEmpty()).isTrue();
        assertThat(storedPlayer(runningRoomId).getStatus())
                .isEqualTo(RunningPlayerStatus.COMPLETED);
        assertThat(storedRoom(runningRoomId).getStatus()).isEqualTo(RunningRoomStatus.FINISHED);
    }

    @Test
    @DisplayName("종료하면 활성 신청이 끝나 다음 러닝을 개시할 수 있다")
    void allowsNextRunAfterFinish() {
        // given -> 끝내지 않으면 AlreadyRunningException으로 막히는 자리다
        UUID userId = onboardedUser(EMAIL, NICKNAME);
        Long runningRoomId = runningRoom(userId);
        runFor(userId, runningRoomId, 400);
        finish(userId, runningRoomId);

        // when
        Long nextRoomId = openSoloRoomHandler.handle(
                new OpenSoloRoomCommand(userId)).runningRoomId();

        // then -> deleted_at을 비우면 활성 신청으로 남아 여기서 걸린다
        assertThat(nextRoomId).isNotEqualTo(runningRoomId);
        assertThat(runningStore.roomCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("남의 방을 끝내려 하면 거부한다")
    void rejectsNonRoomPlayer() {
        // given -> 각자 자기 방을 열었다
        UUID owner = onboardedUser(EMAIL, NICKNAME);
        Long runningRoomId = runningRoom(owner);
        UUID stranger = onboardedUser("stranger@runiverse.com", "낯선러너");

        // when & then
        assertThatThrownBy(() -> finish(stranger, runningRoomId))
                .isInstanceOf(NotRoomPlayerException.class);
        assertThat(storedPlayer(runningRoomId).getStatus())
                .isEqualTo(RunningPlayerStatus.RUNNING);
    }
}
