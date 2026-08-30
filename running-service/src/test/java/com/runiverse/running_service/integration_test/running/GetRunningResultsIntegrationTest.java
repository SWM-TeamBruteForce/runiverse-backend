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
import com.runiverse.running_service.application.running.exception.RunningResultNotFoundException;
import com.runiverse.running_service.application.running.port.out.TrackPoint;
import com.runiverse.running_service.application.running.query.result.GetRunningResultsHandler;
import com.runiverse.running_service.application.running.query.result.GetRunningResultsQuery;
import com.runiverse.running_service.application.running.query.result.GetRunningResultsResult;
import com.runiverse.running_service.application.user.command.onboarding.CompleteOnboardingCommand;
import com.runiverse.running_service.application.user.command.onboarding.CompleteOnboardingHandler;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.running.record.RunningRecord;
import com.runiverse.running_service.integration_test.IntegrationTestSupport;
import com.runiverse.running_service.integration_test.fake.FakeViewUrlGenerator;
import com.runiverse.running_service.integration_test.fake.InMemoryPlayerProfileStore;
import com.runiverse.running_service.integration_test.fake.InMemoryRunningResultStore;
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
import static org.assertj.core.api.Assertions.within;

@DisplayName("러닝 결과 조회 통합 테스트")
public class GetRunningResultsIntegrationTest extends IntegrationTestSupport {

    private static final String PASSWORD = "Password123!";
    private static final String EMAIL = "runner@runiverse.com";
    private static final String OTHER_EMAIL = "other@runiverse.com";
    private static final String NICKNAME = "러너킴";
    private static final String OTHER_NICKNAME = "구경꾼";
    private static final int AVG_PACE = 330;
    private static final BigDecimal WEIGHT = new BigDecimal("70.0");
    private static final LocalDateTime TRACK_START = LocalDateTime.of(2026, 8, 27, 19, 0, 0);
    private static final double METERS_PER_DEGREE = Math.toRadians(1) * 6_371_008.8;
    private static final double PRECISION = 1e-5;

    private static final RunningFinishProperties PROPERTIES = new RunningFinishProperties(
            0.8, 10, 100, 60, 3.0);

    private SignUpHandler signUpHandler;
    private CompleteOnboardingHandler completeOnboardingHandler;
    private OpenSoloRoomHandler openSoloRoomHandler;
    private StartRunningHandler startRunningHandler;
    private UpdateRunningLocationHandler updateRunningLocationHandler;
    private FinishRunningHandler finishRunningHandler;

    private InMemoryPlayerProfileStore playerProfileStore;
    private FakeViewUrlGenerator viewUrlGenerator;
    private GetRunningResultsHandler handler;

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
                runningTrackStore,        // AppendRunningTrackPort
                runningDistanceStore,     // LoadRunningDistancePort
                runningDistanceStore,     // SaveRunningDistancePort
                runningProgressPublisher  // PublishRunningProgressPort
        );
        finishRunningHandler = new FinishRunningHandler(
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

        playerProfileStore = new InMemoryPlayerProfileStore(userStore, onboardingStore);
        viewUrlGenerator = new FakeViewUrlGenerator();
        InMemoryRunningResultStore resultStore =
                new InMemoryRunningResultStore(runningStore, runningRecordStore);
        handler = new GetRunningResultsHandler(
                runningStore,       // LoadRunningRoomPort
                resultStore,        // LoadRunningResultPlayersPort
                resultStore,        // LoadRunningResultRecordPort
                playerProfileStore, // LoadPlayerProfilesPort
                viewUrlGenerator    // GenerateViewUrlPort
        );
    }

    @Test
    @DisplayName("완주한 러닝을 조회하면 기록 지표와 경로가 그대로 실린다")
    void 완주한_러닝을_조회한다() {
        // given -> 약 1,000m를 뛰고 끝냈다
        UUID userId = onboardedUser(EMAIL, NICKNAME);
        Long runningRoomId = finishedRunning(userId);
        RunningRecord record = runningRecordStore.find(runningRoomId, new UserId(userId))
                .orElseThrow();

        // when
        GetRunningResultsResult result =
                handler.handle(new GetRunningResultsQuery(runningRoomId, userId));

        // then -> 최상위 세 값은 본인 기록에서 나온다
        assertThat(result.runningRoomId()).isEqualTo(runningRoomId);
        assertThat(result.startedAt()).isEqualTo(record.getPeriod().startAt());
        assertThat(result.finishedAt()).isEqualTo(record.getPeriod().endAt());

        GetRunningResultsResult.Player me = result.players().get(0);
        assertThat(result.players()).hasSize(1);
        assertThat(me.userId()).isEqualTo(userId);
        assertThat(me.nickname()).isEqualTo(NICKNAME);
        assertThat(me.isMe()).isTrue();
        assertThat(me.isDeleted()).isFalse();
        assertThat(me.status()).isEqualTo("COMPLETED");
        assertThat(me.totalDistanceMeters()).isEqualTo(record.getTotalDistance().meters());
        assertThat(me.totalDurationSeconds()).isEqualTo(record.getTotalDuration().seconds());
        assertThat(me.totalCaloriesKcal()).isEqualTo(record.getTotalCalories().kcal());
        assertThat(me.averagePaceSecondsPerKm()).isEqualTo(record.getAvgPace().secondsPerKm());
        assertThat(me.averageCadenceSpm()).isEqualTo(168);
    }

    @Test
    @DisplayName("경로는 폴리라인을 푼 좌표로 나간다 -> 첫 점이 출발 지점이다")
    void 경로를_좌표로_내린다() {
        // given
        UUID userId = onboardedUser(EMAIL, NICKNAME);
        Long runningRoomId = finishedRunning(userId);

        // when
        GetRunningResultsResult result =
                handler.handle(new GetRunningResultsQuery(runningRoomId, userId));

        // then -> 북쪽으로만 달렸으므로 경도는 그대로고 위도만 커진다
        assertThat(result.routes()).isNotEmpty();
        assertThat(result.routes().get(0).latitude()).isCloseTo(37.5, within(PRECISION));
        assertThat(result.routes().get(0).longitude()).isCloseTo(127.0, within(PRECISION));
        assertThat(result.routes().get(result.routes().size() - 1).latitude())
                .isGreaterThan(result.routes().get(0).latitude());
    }

    @Test
    @DisplayName("아직 뛰는 중이면 지표와 경로가 비어 있다 -> 방이 STARTED인 현재 스냅샷이다")
    void 아직_뛰는_중이면_비어_있다() {
        // given -> RUNNING_START까지만 하고 끝내지 않았다
        UUID userId = onboardedUser(EMAIL, NICKNAME);
        Long runningRoomId = startedRunning(userId);
        runFor(userId, runningRoomId, 400);

        // when
        GetRunningResultsResult result =
                handler.handle(new GetRunningResultsQuery(runningRoomId, userId));

        // then
        assertThat(result.startedAt()).isNull();
        assertThat(result.finishedAt()).isNull();
        assertThat(result.routes()).isNull();

        GetRunningResultsResult.Player me = result.players().get(0);
        assertThat(me.status()).isEqualTo("RUNNING");
        assertThat(me.totalDistanceMeters()).isNull();
        assertThat(me.totalCaloriesKcal()).isNull();
    }

    @Test
    @DisplayName("사진이 없는 참가자는 URL을 만들지 않는다")
    void 사진이_없으면_URL도_없다() {
        // given -> 온보딩만 마친 사용자는 프로필 사진이 없다
        UUID userId = onboardedUser(EMAIL, NICKNAME);
        Long runningRoomId = finishedRunning(userId);

        // when
        GetRunningResultsResult result =
                handler.handle(new GetRunningResultsQuery(runningRoomId, userId));

        // then
        assertThat(result.players().get(0).profileImageUrl()).isNull();
        assertThat(viewUrlGenerator.isEmpty()).isTrue();
    }

    @Test
    @DisplayName("탈퇴한 참가자는 공통 탈퇴 유저 형식으로 나간다 -> 기록은 남는다")
    void 탈퇴자는_공통_형식이다() {
        // given -> 완주한 뒤 users 행이 지워졌다
        UUID userId = onboardedUser(EMAIL, NICKNAME);
        Long runningRoomId = finishedRunning(userId);
        playerProfileStore.withdraw(userId);

        // when
        GetRunningResultsResult result =
                handler.handle(new GetRunningResultsQuery(runningRoomId, userId));

        // then
        GetRunningResultsResult.Player player = result.players().get(0);
        assertThat(player.userId()).isEqualTo(userId);
        assertThat(player.nickname()).isEqualTo("탈퇴한 사용자");
        assertThat(player.profileImageUrl()).isNull();
        assertThat(player.isDeleted()).isTrue();
        assertThat(player.totalDistanceMeters()).isNotNull();
    }

    @Test
    @DisplayName("같은 방 참가자가 아니면 403이다")
    void 참가자가_아니면_403이다() {
        // given -> 남이 뛴 방을 조회한다
        UUID runner = onboardedUser(EMAIL, NICKNAME);
        Long runningRoomId = finishedRunning(runner);
        UUID stranger = onboardedUser(OTHER_EMAIL, OTHER_NICKNAME);

        // when & then
        assertThatThrownBy(() -> handler.handle(
                new GetRunningResultsQuery(runningRoomId, stranger)))
                .isInstanceOf(NotRoomPlayerException.class);
    }

    @Test
    @DisplayName("없는 방은 404다 -> 참가자 판정보다 먼저 끊는다")
    void 없는_방은_404다() {
        // given
        UUID userId = onboardedUser(EMAIL, NICKNAME);

        // when & then
        assertThatThrownBy(() -> handler.handle(new GetRunningResultsQuery(99_999L, userId)))
                .isInstanceOf(RunningResultNotFoundException.class);
    }

    private UUID onboardedUser(String email, String nickname) {
        UUID userId = signUpHandler.handle(
                new SignUpCommand(issueVerificationTicket(email), PASSWORD)).userId();
        completeOnboardingHandler.handle(new CompleteOnboardingCommand(
                userId, nickname, "MALE", LocalDate.of(1998, 5, 20),
                AVG_PACE, WEIGHT, new BigDecimal("175.0")));
        return userId;
    }

    private Long startedRunning(UUID userId) {
        Long runningRoomId = openSoloRoomHandler.handle(
                new OpenSoloRoomCommand(userId)).runningRoomId();
        startRunningHandler.handle(new StartRunningCommand(userId, runningRoomId));
        return runningRoomId;
    }

    private Long finishedRunning(UUID userId) {
        Long runningRoomId = startedRunning(userId);
        runFor(userId, runningRoomId, 400);
        finishRunningHandler.handle(new FinishRunningCommand(runningRoomId, userId, false));
        return runningRoomId;
    }

    // 북쪽으로 초당 2.5m씩 달린 좌표를 밀어 넣는다
    private void runFor(UUID userId, Long runningRoomId, int count) {
        List<TrackPoint> points = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            points.add(new TrackPoint(i, 37.5 + i * 2.5 / METERS_PER_DEGREE, 127.0,
                    null, 5.0, null, null, 168, null, TRACK_START.plusSeconds(i)));
        }
        updateRunningLocationHandler.handle(
                new UpdateRunningLocationCommand(userId, runningRoomId, null, points));
    }
}
