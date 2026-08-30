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
import com.runiverse.running_service.application.running.port.out.RoutePoint;
import com.runiverse.running_service.application.running.port.out.TrackPoint;
import com.runiverse.running_service.application.running.query.split.GetRunningSplitResultsHandler;
import com.runiverse.running_service.application.running.query.split.GetRunningSplitResultsQuery;
import com.runiverse.running_service.application.running.query.split.GetRunningSplitResultsResult;
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

@DisplayName("러닝 구간별 결과 조회 통합 테스트")
public class GetRunningSplitResultsIntegrationTest extends IntegrationTestSupport {

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
    private static final int SPLIT_DISTANCE = 10;

    private static final RunningFinishProperties PROPERTIES = new RunningFinishProperties(
            0.8, SPLIT_DISTANCE, 100, 60, 3.0);

    private SignUpHandler signUpHandler;
    private CompleteOnboardingHandler completeOnboardingHandler;
    private OpenSoloRoomHandler openSoloRoomHandler;
    private StartRunningHandler startRunningHandler;
    private UpdateRunningLocationHandler updateRunningLocationHandler;
    private FinishRunningHandler finishRunningHandler;
    private GetRunningSplitResultsHandler handler;

    @BeforeEach
    void setUp() {
        signUpHandler = newSignUpHandler();
        completeOnboardingHandler = new CompleteOnboardingHandler(
                userStore, onboardingStore, onboardingStore, onboardingStore);
        openSoloRoomHandler = new OpenSoloRoomHandler(
                runningStore, onboardingStore, runningStore, runningStore);
        startRunningHandler = new StartRunningHandler(
                runningStore, runningStore, runningStore, runningStore);
        updateRunningLocationHandler = new UpdateRunningLocationHandler(
                runningTrackStore, runningDistanceStore, runningDistanceStore,
                runningProgressPublisher);
        finishRunningHandler = new FinishRunningHandler(
                runningStore, runningStore, runningTrackStore, onboardingStore, weatherProvider,
                gpsTrackUploader, runningRecordStore, runningStore, runningTrackStore,
                runningStore, runningStore, PROPERTIES);

        InMemoryRunningResultStore resultStore =
                new InMemoryRunningResultStore(runningStore, runningRecordStore);
        handler = new GetRunningSplitResultsHandler(
                runningStore,                                                    // LoadRunningRoomPort
                resultStore,                                                     // LoadRunningResultPlayersPort
                resultStore,                                                     // LoadRunningResultRecordPort
                resultStore,                                                     // LoadRunningSplitsPort
                new InMemoryPlayerProfileStore(userStore, onboardingStore),       // LoadPlayerProfilesPort
                new FakeViewUrlGenerator(),                                       // GenerateViewUrlPort
                PROPERTIES);
    }

    @Test
    @DisplayName("완주하면 저장된 구간 수만큼 비교표가 나온다")
    void 구간이_모두_실린다() {
        // given -> 약 990m를 뛰었다. 10m 구간이라 99개가 저장된다
        UUID userId = onboardedUser(EMAIL, NICKNAME);
        Long runningRoomId = finishedRunning(userId);
        RunningRecord record = runningRecordStore.find(runningRoomId, new UserId(userId))
                .orElseThrow();

        // when
        GetRunningSplitResultsResult result =
                handler.handle(new GetRunningSplitResultsQuery(runningRoomId, userId));

        // then
        assertThat(result.splitDistanceMeters()).isEqualTo(SPLIT_DISTANCE);
        assertThat(result.totalDistanceMeters()).isEqualTo(record.getTotalDistance().meters());
        assertThat(result.startedAt()).isEqualTo(record.getPeriod().startAt());
        assertThat(result.finishedAt()).isEqualTo(record.getPeriod().endAt());
        assertThat(result.splits()).hasSize(record.getSplits().size());
        assertThat(result.players()).hasSize(1);
        assertThat(result.players().get(0).isMe()).isTrue();
    }

    @Test
    @DisplayName("구간 번호가 1부터 오름차순이고 경계가 거리로 이어진다")
    void 구간_경계가_이어진다() {
        // given
        UUID userId = onboardedUser(EMAIL, NICKNAME);
        Long runningRoomId = finishedRunning(userId);

        // when
        List<GetRunningSplitResultsResult.Split> splits =
                handler.handle(new GetRunningSplitResultsQuery(runningRoomId, userId)).splits();

        // then -> N번 구간의 끝 거리가 N+1번의 시작 거리다
        assertThat(splits.get(0).splitNumber()).isEqualTo(1);
        assertThat(splits.get(0).startDistanceMeters()).isZero();
        for (int i = 1; i < splits.size(); i++) {
            assertThat(splits.get(i).splitNumber()).isEqualTo(i + 1);
            assertThat(splits.get(i).startDistanceMeters())
                    .isEqualTo(splits.get(i - 1).endDistanceMeters());
        }
    }

    @Test
    @DisplayName("구간 경로를 이어붙이면 전체 경로가 된다 -> 경계점이 겹친다")
    void 구간_경로가_이어진다() {
        // given
        UUID userId = onboardedUser(EMAIL, NICKNAME);
        Long runningRoomId = finishedRunning(userId);

        // when
        List<GetRunningSplitResultsResult.Split> splits =
                handler.handle(new GetRunningSplitResultsQuery(runningRoomId, userId)).splits();

        // then -> 앞 구간의 끝 점과 뒤 구간의 시작 점이 같다. subList의 +1을 빼면 여기서 깨진다
        for (int i = 1; i < splits.size(); i++) {
            List<RoutePoint> previous = splits.get(i - 1).routes();
            List<RoutePoint> current = splits.get(i).routes();
            assertThat(previous).isNotEmpty();
            assertThat(current).isNotEmpty();
            assertThat(previous.get(previous.size() - 1).latitude())
                    .isCloseTo(current.get(0).latitude(), within(PRECISION));
            assertThat(previous.get(previous.size() - 1).longitude())
                    .isCloseTo(current.get(0).longitude(), within(PRECISION));
        }
        // 북쪽으로만 달렸으므로 첫 구간의 출발점은 시작 좌표다
        assertThat(splits.get(0).routes().get(0).latitude()).isCloseTo(37.5, within(PRECISION));
        assertThat(splits.get(0).routes().get(0).longitude()).isCloseTo(127.0, within(PRECISION));
    }

    @Test
    @DisplayName("10m 구간의 고도 변화는 null이다 -> 노이즈 임계값을 넘는 표본이 없다")
    void 고도_변화는_null이다() {
        // given -> 고도를 싣지 않은 좌표로 뛰었다(단말이 고도를 못 재는 경우)
        UUID userId = onboardedUser(EMAIL, NICKNAME);
        Long runningRoomId = finishedRunning(userId);

        // when
        List<GetRunningSplitResultsResult.Split> splits =
                handler.handle(new GetRunningSplitResultsQuery(runningRoomId, userId)).splits();

        // then
        assertThat(splits)
                .allSatisfy(split -> assertThat(split.players().get(0).elevationChangeMeters())
                        .isNull());
    }

    @Test
    @DisplayName("아직 뛰는 중이면 최상위가 null이고 구간이 비어 있다")
    void 뛰는_중이면_비어_있다() {
        // given -> RUNNING_START까지만 했다
        UUID userId = onboardedUser(EMAIL, NICKNAME);
        Long runningRoomId = startedRunning(userId);
        runFor(userId, runningRoomId, 400);

        // when
        GetRunningSplitResultsResult result =
                handler.handle(new GetRunningSplitResultsQuery(runningRoomId, userId));

        // then -> 기록이 없으면 최상위 players에서도 빠진다(6-1과 다른 규칙)
        assertThat(result.totalDistanceMeters()).isNull();
        assertThat(result.startedAt()).isNull();
        assertThat(result.splits()).isEmpty();
        assertThat(result.players()).isEmpty();
    }

    @Test
    @DisplayName("같은 방 참가자가 아니면 403이다")
    void 참가자가_아니면_403이다() {
        // given
        UUID runner = onboardedUser(EMAIL, NICKNAME);
        Long runningRoomId = finishedRunning(runner);
        UUID stranger = onboardedUser(OTHER_EMAIL, OTHER_NICKNAME);

        // when & then
        assertThatThrownBy(() -> handler.handle(
                new GetRunningSplitResultsQuery(runningRoomId, stranger)))
                .isInstanceOf(NotRoomPlayerException.class);
    }

    @Test
    @DisplayName("없는 방은 404다")
    void 없는_방은_404다() {
        // given
        UUID userId = onboardedUser(EMAIL, NICKNAME);

        // when & then
        assertThatThrownBy(() -> handler.handle(
                new GetRunningSplitResultsQuery(99_999L, userId)))
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
