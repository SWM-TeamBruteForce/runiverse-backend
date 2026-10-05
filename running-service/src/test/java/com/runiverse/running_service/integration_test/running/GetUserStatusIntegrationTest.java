package com.runiverse.running_service.integration_test.running;

import com.runiverse.running_service.application.auth.command.signup.SignUpCommand;
import com.runiverse.running_service.application.auth.command.signup.SignUpHandler;
import com.runiverse.running_service.application.match.common.MatchProperties;
import com.runiverse.running_service.application.running.command.finish.FinishRunningCommand;
import com.runiverse.running_service.application.running.command.finish.FinishRunningHandler;
import com.runiverse.running_service.application.running.command.location.UpdateRunningFinishJudge;
import com.runiverse.running_service.application.running.command.location.UpdateRunningLocationCommand;
import com.runiverse.running_service.application.running.command.location.UpdateRunningLocationHandler;
import com.runiverse.running_service.application.running.command.solo.OpenSoloRoomCommand;
import com.runiverse.running_service.application.running.command.solo.OpenSoloRoomHandler;
import com.runiverse.running_service.application.running.command.start.StartRunningCommand;
import com.runiverse.running_service.application.running.command.start.StartRunningHandler;
import com.runiverse.running_service.application.running.common.RunningFinishProperties;
import com.runiverse.running_service.application.running.common.RunningFinisher;
import com.runiverse.running_service.application.running.port.out.TrackPoint;
import com.runiverse.running_service.application.running.query.status.GetUserStatusHandler;
import com.runiverse.running_service.application.running.query.status.GetUserStatusQuery;
import com.runiverse.running_service.application.running.query.status.GetUserStatusResult;
import com.runiverse.running_service.application.running.query.status.UserRunningStatus;
import com.runiverse.running_service.application.user.command.onboarding.CompleteOnboardingCommand;
import com.runiverse.running_service.application.user.command.onboarding.CompleteOnboardingHandler;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.running.player.RunningPlayer;
import com.runiverse.running_service.domain.running.room.RunningRoom;
import com.runiverse.running_service.domain.running.room.vo.RunningRoomType;
import com.runiverse.running_service.integration_test.IntegrationTestSupport;
import com.runiverse.running_service.integration_test.fake.InMemoryMatchCooldownStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static java.time.temporal.ChronoUnit.SECONDS;

@DisplayName("유저 현재 상태 조회 통합 테스트")
public class GetUserStatusIntegrationTest extends IntegrationTestSupport {

    private static final String PASSWORD = "Password123!";
    private static final String EMAIL = "runner@runiverse.com";
    private static final String OTHER_EMAIL = "other@runiverse.com";
    private static final String NICKNAME = "러너킴";
    private static final String OTHER_NICKNAME = "구경꾼";
    private static final int AVG_PACE = 330;
    private static final BigDecimal WEIGHT = new BigDecimal("70.0");
    private static final int TARGET_DISTANCE = 5_000;
    private static final double METERS_PER_DEGREE = Math.toRadians(1) * 6_371_008.8;

    // 운영 설정과 같은 값
    private static final Duration CLOSE_OFFSET = Duration.ofMinutes(10);
    private static final Duration COOLDOWN = Duration.ofMinutes(20);
    private static final MatchProperties MATCH_PROPERTIES = new MatchProperties(
            CLOSE_OFFSET, Duration.ofSeconds(10), Duration.ofHours(6), 10, COOLDOWN);
    private static final RunningFinishProperties FINISH_PROPERTIES = new RunningFinishProperties(
            0.8, 10, 100, 60, 3.0, COOLDOWN);

    private SignUpHandler signUpHandler;
    private CompleteOnboardingHandler completeOnboardingHandler;
    private OpenSoloRoomHandler openSoloRoomHandler;
    private StartRunningHandler startRunningHandler;
    private UpdateRunningLocationHandler updateRunningLocationHandler;
    private FinishRunningHandler finishRunningHandler;
    private InMemoryMatchCooldownStore matchCooldownStore;
    private GetUserStatusHandler handler;

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
                runningStore,     // LockRunningPlayerPort
                runningStore,     // LockRunningRoomPort
                runningStore,     // UpdateRunningRoomPort
                runningStore      // UpdateRunningPlayerPort
        );
        RunningFinisher runningFinisher = new RunningFinisher(
                runningStore,       // LockRunningRoomPort
                runningStore,       // LockRunningPlayerPort
                runningTrackStore,  // LoadRunningTrackPort
                onboardingStore,    // LoadUserWeightPort
                weatherProvider,    // LoadWeatherPort
                gpsTrackUploader,   // SaveGpsTrackPort
                runningRecordStore, // CreateRunningRecordPort
                runningStore,       // UpdateRunningPlayerPort
                runningTrackStore,  // DeleteRunningTrackPort
                runningStore,       // ExistsRunningPlayerPort
                runningStore,       // CountStartedRunningPlayerPort
                runningStore,       // UpdateRunningRoomPort
                // 쿨다운 발급은 이 흐름의 주제가 아니다 — 아무것도 하지 않는다
                (userId, cooldown) -> {
                },                  // StartMatchCooldownPort
                runningRecordStore, // ExistsRunningRecordPort
                runningRecordStore, // LoadRecentRunningPacesPort
                onboardingStore,    // UpdateUserAvgPacePort
                event -> {          // ApplicationEventPublisher
                },
                FINISH_PROPERTIES
        );
        updateRunningLocationHandler = new UpdateRunningLocationHandler(
                runningTrackStore,        // AppendRunningTrackPort
                runningDistanceStore,     // LoadRunningDistancePort
                runningDistanceStore,     // SaveRunningDistancePort
                runningProgressPublisher, // PublishRunningProgressPort
                newUpdateRunningComboJudge(),
                new UpdateRunningFinishJudge(runningFinisher)
        );
        finishRunningHandler = new FinishRunningHandler(runningFinisher);
        matchCooldownStore = new InMemoryMatchCooldownStore();
        handler = new GetUserStatusHandler(
                runningStore,        // LoadUserStatusPort
                matchCooldownStore,  // MatchCooldownPort
                MATCH_PROPERTIES
        );
    }

    @Test
    @DisplayName("신청이 없으면 IDLE이고 방 값은 비어 있다")
    void isIdleWithoutApplication() {
        // given
        UUID userId = onboardedUser(EMAIL, NICKNAME);

        // when
        GetUserStatusResult result = statusOf(userId);

        // then
        assertThat(result.status()).isEqualTo(UserRunningStatus.IDLE);
        assertThat(result.type()).isNull();
        assertThat(result.runningRoomId()).isNull();
        assertThat(result.scheduledStartAt()).isNull();
        assertThat(result.targetDistanceMeters()).isNull();
        assertThat(result.cooldownUntil()).isNull();
    }

    @Test
    @DisplayName("솔로 방을 열면 READY이고 목표 거리는 비어 있다")
    void soloRoomIsReadyWithoutTarget() {
        // given
        UUID userId = onboardedUser(EMAIL, NICKNAME);

        // when
        Long runningRoomId = openSoloRoomHandler.handle(
                new OpenSoloRoomCommand(userId)).runningRoomId();

        // then -> 솔로는 모집 없이 확정된 채로 태어난다
        GetUserStatusResult result = statusOf(userId);
        assertThat(result.status()).isEqualTo(UserRunningStatus.READY);
        assertThat(result.type()).isEqualTo(RunningRoomType.SOLO);
        assertThat(result.runningRoomId()).isEqualTo(runningRoomId);
        assertThat(result.scheduledStartAt())
                .isEqualTo(runningStore.findRoom(runningRoomId).orElseThrow().getStartAt());
        // 신청 행에는 무제한 거리가 들어 있지만 방 값이 정본이다
        assertThat(result.targetDistanceMeters()).isNull();
    }

    @Test
    @DisplayName("러닝을 시작하면 RUNNING, 끝내면 다시 IDLE이다")
    void followsRunningLifecycle() {
        // given
        UUID userId = onboardedUser(EMAIL, NICKNAME);
        Long runningRoomId = openSoloRoomHandler.handle(
                new OpenSoloRoomCommand(userId)).runningRoomId();

        // when
        startRunningHandler.handle(new StartRunningCommand(userId, runningRoomId));

        // then
        assertThat(statusOf(userId).status()).isEqualTo(UserRunningStatus.RUNNING);
        assertThat(statusOf(userId).runningRoomId()).isEqualTo(runningRoomId);

        // when
        runFor(userId, runningRoomId);
        finishRunningHandler.handle(new FinishRunningCommand(runningRoomId, userId, false));

        // then
        GetUserStatusResult finished = statusOf(userId);
        assertThat(finished.status()).isEqualTo(UserRunningStatus.IDLE);
        assertThat(finished.runningRoomId()).isNull();
    }

    @Test
    @DisplayName("모집 마감 전의 매칭 방은 WAITING이고 목표 거리가 실린다")
    void matchingRoomBeforeCloseIsWaiting() {
        // given
        UUID userId = onboardedUser(EMAIL, NICKNAME);
        LocalDateTime startAt = LocalDateTime.now().plusHours(1).truncatedTo(SECONDS);
        Long runningRoomId = matchingRoom(userId, startAt);

        // when
        GetUserStatusResult result = statusOf(userId);

        // then
        assertThat(result.status()).isEqualTo(UserRunningStatus.WAITING);
        assertThat(result.type()).isEqualTo(RunningRoomType.MATCH);
        assertThat(result.runningRoomId()).isEqualTo(runningRoomId);
        assertThat(result.scheduledStartAt()).isEqualTo(startAt);
        assertThat(result.targetDistanceMeters()).isEqualTo(TARGET_DISTANCE);
    }

    @Test
    @DisplayName("마감이 지났는데 아직 MATCHING인 방은 READY로 답한다")
    void matchingRoomAfterCloseIsReady() {
        // given -> 확정 예약이 아직 깨지 않아 방은 MATCHING 그대로다
        UUID userId = onboardedUser(EMAIL, NICKNAME);
        matchingRoom(userId, LocalDateTime.now().plus(CLOSE_OFFSET).minusMinutes(1));

        // when & then
        assertThat(statusOf(userId).status()).isEqualTo(UserRunningStatus.READY);
    }

    @Test
    @DisplayName("제재 쿨다운이 남아 있으면 IDLE에도 해제 시각이 실린다")
    void carriesCooldownWhileIdle() {
        // given
        UUID userId = onboardedUser(EMAIL, NICKNAME);
        matchCooldownStore.start(new UserId(userId), COOLDOWN);

        // when
        GetUserStatusResult result = statusOf(userId);

        // then
        assertThat(result.status()).isEqualTo(UserRunningStatus.IDLE);
        assertThat(result.cooldownUntil())
                .isCloseTo(LocalDateTime.now().plus(COOLDOWN), within(5, SECONDS));
    }

    @Test
    @DisplayName("다른 사용자의 신청이 섞이지 않는다")
    void doesNotLeakOtherUsersApplication() {
        // given
        UUID userId = onboardedUser(EMAIL, NICKNAME);
        UUID otherUserId = onboardedUser(OTHER_EMAIL, OTHER_NICKNAME);
        openSoloRoomHandler.handle(new OpenSoloRoomCommand(otherUserId));

        // when & then
        assertThat(statusOf(userId).status()).isEqualTo(UserRunningStatus.IDLE);
        assertThat(statusOf(otherUserId).status()).isEqualTo(UserRunningStatus.READY);
    }

    private GetUserStatusResult statusOf(UUID userId) {
        return handler.handle(new GetUserStatusQuery(userId));
    }

    private UUID onboardedUser(String email, String nickname) {
        UUID userId = signUpHandler.handle(
                new SignUpCommand(issueVerificationTicket(email), PASSWORD)).userId();
        completeOnboardingHandler.handle(new CompleteOnboardingCommand(
                userId, nickname, "MALE", LocalDate.of(1998, 5, 20),
                AVG_PACE, WEIGHT, new BigDecimal("175.0")));
        return userId;
    }

    // 마감이 지난 방은 신청으로 만들 수 없어(마감 슬롯은 막힌다) 신청·방·배정 행을 직접 만든다
    private Long matchingRoom(UUID userId, LocalDateTime startAt) {
        RunningPlayer player = runningStore.create(
                RunningPlayer.request(userId, AVG_PACE, TARGET_DISTANCE, startAt));
        RunningRoom room = runningStore.create(RunningRoom.openMatch(
                new UserId(userId), player.getRunningPlayerId().orElseThrow(),
                AVG_PACE, TARGET_DISTANCE, startAt));
        return room.getRunningRoomId().orElseThrow().value();
    }

    // 기록이 남을 만큼(100m·60초 이상) 북쪽으로 초당 2.5m씩 달린 좌표를 밀어 넣는다
    private void runFor(UUID userId, Long runningRoomId) {
        LocalDateTime trackStart = LocalDateTime.now().minusSeconds(400);
        List<TrackPoint> points = new ArrayList<>(400);
        for (int i = 0; i < 400; i++) {
            points.add(new TrackPoint(i, 37.5 + i * 2.5 / METERS_PER_DEGREE, 127.0,
                    null, 5.0, null, null, 168, null, trackStart.plusSeconds(i)));
        }
        updateRunningLocationHandler.handle(
                new UpdateRunningLocationCommand(userId, runningRoomId, null, points));
    }
}
