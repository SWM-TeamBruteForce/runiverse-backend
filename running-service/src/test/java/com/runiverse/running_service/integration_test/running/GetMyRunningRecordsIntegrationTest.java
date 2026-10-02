package com.runiverse.running_service.integration_test.running;

import com.runiverse.running_service.application.auth.command.signup.SignUpCommand;
import com.runiverse.running_service.application.auth.command.signup.SignUpHandler;
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
import com.runiverse.running_service.application.running.query.record.GetMyRunningRecordsHandler;
import com.runiverse.running_service.application.running.query.record.GetMyRunningRecordsQuery;
import com.runiverse.running_service.application.running.query.record.GetMyRunningRecordsResult;
import com.runiverse.running_service.application.user.command.onboarding.CompleteOnboardingCommand;
import com.runiverse.running_service.application.user.command.onboarding.CompleteOnboardingHandler;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.running.record.RunningRecord;
import com.runiverse.running_service.domain.running.room.vo.RunningRoomType;
import com.runiverse.running_service.integration_test.IntegrationTestSupport;
import com.runiverse.running_service.integration_test.fake.InMemoryRunningRecordListStore;
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

@DisplayName("내 러닝 기록 목록 조회 통합 테스트")
public class GetMyRunningRecordsIntegrationTest extends IntegrationTestSupport {

    private static final String PASSWORD = "Password123!";
    private static final String EMAIL = "runner@runiverse.com";
    private static final String OTHER_EMAIL = "other@runiverse.com";
    private static final String NICKNAME = "러너킴";
    private static final String OTHER_NICKNAME = "구경꾼";
    private static final int AVG_PACE = 330;
    private static final BigDecimal WEIGHT = new BigDecimal("70.0");
    private static final LocalDate FIRST_DAY = LocalDate.of(2026, 8, 27);
    private static final LocalDate SECOND_DAY = FIRST_DAY.plusDays(1);
    private static final LocalDateTime FIRST_START = FIRST_DAY.atTime(19, 0);
    // 하루의 끝에 걸친 러닝 — to 날짜가 그날 자정까지 포함하는지 본다
    private static final LocalDateTime LATE_NIGHT_START = SECOND_DAY.atTime(23, 50);
    private static final int TRACK_POINTS = 400;
    private static final double METERS_PER_DEGREE = Math.toRadians(1) * 6_371_008.8;

    // 조기 종료 제재로 매칭 신청이 막히는 기간 — 이 테스트의 주제는 아니다
    private static final Duration COOLDOWN = Duration.ofMinutes(20);
    private static final RunningFinishProperties PROPERTIES = new RunningFinishProperties(
            0.8, 10, 100, 60, 3.0, COOLDOWN);

    private SignUpHandler signUpHandler;
    private CompleteOnboardingHandler completeOnboardingHandler;
    private OpenSoloRoomHandler openSoloRoomHandler;
    private StartRunningHandler startRunningHandler;
    private UpdateRunningLocationHandler updateRunningLocationHandler;
    private FinishRunningHandler finishRunningHandler;
    private GetMyRunningRecordsHandler handler;

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
                runningStore,       // UpdateRunningRoomPort
                // 쿨다운 발급은 이 테스트의 주제가 아니다 — 아무것도 하지 않는다
                (userId, cooldown) -> {
                },                  // StartMatchCooldownPort
                runningRecordStore, // ExistsRunningRecordPort
                runningRecordStore, // LoadRecentRunningPacesPort
                onboardingStore,    // UpdateUserAvgPacePort
                PROPERTIES
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
        handler = new GetMyRunningRecordsHandler(
                new InMemoryRunningRecordListStore(runningStore, runningRecordStore)  // LoadMyRunningRecordsPort
        );
    }

    @Test
    @DisplayName("끝낸 러닝은 그날의 목록에 기록 값 그대로 한 건 실린다")
    void listsFinishedRunningOfTheDay() {
        // given -> 약 1,000m를 뛰고 끝냈다
        UUID userId = onboardedUser(EMAIL, NICKNAME);
        Long runningRoomId = finishedRunning(userId, FIRST_START);
        RunningRecord record = runningRecordStore.find(runningRoomId, new UserId(userId))
                .orElseThrow();

        // when
        List<GetMyRunningRecordsResult.RunningRecord> records = recordsOf(userId, FIRST_DAY, FIRST_DAY);

        // then -> 솔로 방은 본인 혼자 출발한다
        assertThat(records).hasSize(1);
        GetMyRunningRecordsResult.RunningRecord listed = records.get(0);
        assertThat(listed.runningRecordId()).isNotNull();
        assertThat(listed.runningRoomId()).isEqualTo(runningRoomId);
        assertThat(listed.type()).isEqualTo(RunningRoomType.SOLO);
        assertThat(listed.playerCount()).isEqualTo(1);
        assertThat(listed.startedAt()).isEqualTo(record.getPeriod().startAt());
        assertThat(listed.totalDistanceMeters()).isEqualTo(record.getTotalDistance().meters());
        assertThat(listed.totalDurationSeconds()).isEqualTo(record.getTotalDuration().seconds());
        assertThat(listed.averagePaceSecondsPerKm()).isEqualTo(record.getAvgPace().secondsPerKm());
        assertThat(listed.routePolyline()).isEqualTo(record.getRoutePolyline().value());
    }

    @Test
    @DisplayName("기간은 시작 시각으로 거르고 to 날짜는 그날 끝까지 포함한다")
    void filtersByStartDateInclusive() {
        // given -> 첫날 저녁, 다음 날 자정 직전에 한 번씩 뛰었다
        UUID userId = onboardedUser(EMAIL, NICKNAME);
        Long firstRoomId = finishedRunning(userId, FIRST_START);
        Long lateNightRoomId = finishedRunning(userId, LATE_NIGHT_START);

        // when & then
        assertThat(recordsOf(userId, FIRST_DAY, FIRST_DAY))
                .extracting(GetMyRunningRecordsResult.RunningRecord::runningRoomId)
                .containsExactly(firstRoomId);
        assertThat(recordsOf(userId, SECOND_DAY, SECOND_DAY))
                .extracting(GetMyRunningRecordsResult.RunningRecord::runningRoomId)
                .containsExactly(lateNightRoomId);
        assertThat(recordsOf(userId, FIRST_DAY, SECOND_DAY))
                .extracting(GetMyRunningRecordsResult.RunningRecord::runningRoomId)
                .containsExactly(firstRoomId, lateNightRoomId);
    }

    @Test
    @DisplayName("아직 뛰는 중인 러닝은 기록이 없어 목록에 없다")
    void excludesRunningInProgress() {
        // given
        UUID userId = onboardedUser(EMAIL, NICKNAME);
        Long runningRoomId = startedRunning(userId);
        runFor(userId, runningRoomId, FIRST_START);

        // when & then
        assertThat(recordsOf(userId, FIRST_DAY, FIRST_DAY)).isEmpty();
    }

    @Test
    @DisplayName("다른 사용자의 기록은 섞이지 않는다")
    void doesNotLeakOtherUsersRecords() {
        // given -> 같은 날 각자 뛰었다
        UUID userId = onboardedUser(EMAIL, NICKNAME);
        UUID otherUserId = onboardedUser(OTHER_EMAIL, OTHER_NICKNAME);
        Long myRoomId = finishedRunning(userId, FIRST_START);
        finishedRunning(otherUserId, FIRST_START);

        // when & then
        assertThat(recordsOf(userId, FIRST_DAY, FIRST_DAY))
                .extracting(GetMyRunningRecordsResult.RunningRecord::runningRoomId)
                .containsExactly(myRoomId);
    }

    private List<GetMyRunningRecordsResult.RunningRecord> recordsOf(UUID userId, LocalDate from,
                                                                    LocalDate to) {
        return handler.handle(new GetMyRunningRecordsQuery(userId, from, to)).runningRecords();
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

    // 기록의 시작 시각은 첫 좌표 시각이다 — 날짜를 가르려면 트랙 시작을 바꾼다
    private Long finishedRunning(UUID userId, LocalDateTime trackStart) {
        Long runningRoomId = startedRunning(userId);
        runFor(userId, runningRoomId, trackStart);
        finishRunningHandler.handle(new FinishRunningCommand(runningRoomId, userId, false));
        return runningRoomId;
    }

    // 북쪽으로 초당 2.5m씩 달린 좌표를 밀어 넣는다
    private void runFor(UUID userId, Long runningRoomId, LocalDateTime trackStart) {
        List<TrackPoint> points = new ArrayList<>(TRACK_POINTS);
        for (int i = 0; i < TRACK_POINTS; i++) {
            points.add(new TrackPoint(i, 37.5 + i * 2.5 / METERS_PER_DEGREE, 127.0,
                    null, 5.0, null, null, 168, null, trackStart.plusSeconds(i)));
        }
        updateRunningLocationHandler.handle(
                new UpdateRunningLocationCommand(userId, runningRoomId, null, points));
    }
}
