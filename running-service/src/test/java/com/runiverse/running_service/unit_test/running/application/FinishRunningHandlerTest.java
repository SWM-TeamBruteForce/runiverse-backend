package com.runiverse.running_service.unit_test.running.application;

import com.github.f4b6a3.uuid.UuidCreator;
import com.runiverse.running_service.application.common.port.out.UpdateUserAvgPacePort;
import com.runiverse.running_service.application.running.command.finish.CalorieCalculator;
import com.runiverse.running_service.application.running.command.finish.FinishRunningCommand;
import com.runiverse.running_service.application.running.command.finish.FinishRunningHandler;
import com.runiverse.running_service.application.running.command.finish.RunningFinishProperties;
import com.runiverse.running_service.application.running.exception.NotRoomPlayerException;
import com.runiverse.running_service.application.running.exception.RunningNotStartableException;
import com.runiverse.running_service.application.running.exception.RunningRoomNotFoundException;
import com.runiverse.running_service.application.running.port.out.CreateRunningRecordPort;
import com.runiverse.running_service.application.running.port.out.DeleteRunningTrackPort;
import com.runiverse.running_service.application.running.port.out.ExistsRunningPlayerPort;
import com.runiverse.running_service.application.running.port.out.ExistsRunningRecordPort;
import com.runiverse.running_service.application.running.port.out.GpsTrackUpload;
import com.runiverse.running_service.application.running.port.out.LoadRecentRunningPacesPort;
import com.runiverse.running_service.application.running.port.out.LoadRoomPlayerPort;
import com.runiverse.running_service.application.running.port.out.RecentRunningPace;
import com.runiverse.running_service.application.running.port.out.LoadRunningRoomPort;
import com.runiverse.running_service.application.running.port.out.LoadRunningTrackPort;
import com.runiverse.running_service.application.running.port.out.LoadUserWeightPort;
import com.runiverse.running_service.application.running.port.out.LoadWeatherPort;
import com.runiverse.running_service.application.running.port.out.RunningTrack;
import com.runiverse.running_service.application.running.port.out.SaveGpsTrackPort;
import com.runiverse.running_service.application.running.port.out.StartMatchCooldownPort;
import com.runiverse.running_service.application.running.port.out.TrackPoint;
import com.runiverse.running_service.application.running.port.out.UpdateRunningPlayerPort;
import com.runiverse.running_service.application.running.port.out.UpdateRunningRoomPort;
import com.runiverse.running_service.application.running.port.out.Weather;
import com.runiverse.running_service.application.user.exception.OnboardingNotCompletedException;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.running.record.RunningRecord;
import com.runiverse.running_service.domain.running.record.RunningSplit;
import com.runiverse.running_service.domain.running.player.RunningPlayer;
import com.runiverse.running_service.domain.running.player.vo.RunningPlayerId;
import com.runiverse.running_service.domain.running.player.vo.RunningPlayerStatus;
import com.runiverse.running_service.domain.running.room.RoomSession;
import com.runiverse.running_service.domain.running.room.RunningRoom;
import com.runiverse.running_service.domain.running.room.SessionDraft;
import com.runiverse.running_service.domain.running.room.vo.RunningRoomId;
import com.runiverse.running_service.domain.running.room.vo.RunningRoomStatus;
import com.runiverse.running_service.domain.running.room.vo.RunningRoomType;
import com.runiverse.running_service.domain.user.vo.AvgPace;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@DisplayName("러닝 종료 단위 테스트")
public class FinishRunningHandlerTest {

    private static final UUID USER_ID = UuidCreator.getTimeOrderedEpoch();
    private static final long PLAYER_ID = 42L;
    private static final long ROOM_ID = 125L;
    private static final int AVG_PACE = 330;                              // 5분 30초/km
    private static final int TARGET = 5_000;
    private static final BigDecimal WEIGHT = new BigDecimal("70.0");
    private static final int CADENCE = 168;
    private static final String GPS_TRACK_KEY = "gps-tracks/user/125/2026-08-27.json";
    // 외부 날씨 API가 붙기 전이라 어댑터는 기본값만 돌려준다(DefaultWeatherAdapter)
    private static final Weather WEATHER = new Weather(0, new BigDecimal("15.0"));
    private static final LocalDateTime PAST = LocalDateTime.now().minusMinutes(30);
    private static final LocalDateTime TRACK_START = LocalDateTime.of(2026, 8, 27, 19, 0, 0);
    // TrackDistance와 같은 지구 반경에서 뽑는다 — 어긋나면 의도한 거리와 측정 거리가 벌어져
    // 80% 경계 테스트가 엉뚱한 쪽으로 넘어간다
    private static final double METERS_PER_DEGREE = Math.toRadians(1) * 6_371_008.8;

    // 조기 종료 제재로 매칭 신청이 막히는 기간 — 취소 이탈(match.cooldown)과 따로 둔다
    private static final Duration COOLDOWN = Duration.ofMinutes(20);
    // 운영 설정 그대로 — 페널티 경계는 목표의 80%다
    private static final RunningFinishProperties PROPERTIES = new RunningFinishProperties(
            0.8, 10, 100, 60, 3.0, COOLDOWN);

    @Mock
    private LoadRunningRoomPort loadRunningRoomPort;

    @Mock
    private LoadRoomPlayerPort loadRoomPlayerPort;

    @Mock
    private LoadRunningTrackPort loadRunningTrackPort;

    @Mock
    private LoadUserWeightPort loadUserWeightPort;

    @Mock
    private LoadWeatherPort loadWeatherPort;

    @Mock
    private SaveGpsTrackPort saveGpsTrackPort;

    @Mock
    private CreateRunningRecordPort createRunningRecordPort;

    @Mock
    private UpdateRunningPlayerPort updateRunningPlayerPort;

    @Mock
    private DeleteRunningTrackPort deleteRunningTrackPort;

    @Mock
    private ExistsRunningPlayerPort existsRunningPlayerPort;

    @Mock
    private ExistsRunningRecordPort existsRunningRecordPort;

    @Mock
    private UpdateRunningRoomPort updateRunningRoomPort;

    @Mock
    private StartMatchCooldownPort startMatchCooldownPort;

    @Mock
    private LoadRecentRunningPacesPort loadRecentRunningPacesPort;

    @Mock
    private UpdateUserAvgPacePort updateUserAvgPacePort;

    @Captor
    private ArgumentCaptor<RunningRecord> recordCaptor;

    @Captor
    private ArgumentCaptor<GpsTrackUpload> uploadCaptor;

    private FinishRunningHandler handler;

    // 설정값은 검증 대상이라 mock이 아니라 실제 값을 넣는다 — 0.8 경계가 이 테스트의 주제다
    @BeforeEach
    void setUp() {
        handler = new FinishRunningHandler(loadRunningRoomPort, loadRoomPlayerPort,
                loadRunningTrackPort, loadUserWeightPort, loadWeatherPort, saveGpsTrackPort,
                createRunningRecordPort, updateRunningPlayerPort, deleteRunningTrackPort,
                existsRunningPlayerPort, updateRunningRoomPort, startMatchCooldownPort,
                existsRunningRecordPort, loadRecentRunningPacesPort, updateUserAvgPacePort,
                PROPERTIES);
        // 이 클래스의 트랙은 대부분 유효 러닝을 통과해 기록이 남는다 —
        // 기록 없이 닫히는 경우만 개별 테스트가 뒤집는다
        lenient().when(existsRunningRecordPort.existsInRoom(new RunningRoomId(ROOM_ID)))
                .thenReturn(true);
    }

    // 종료 시각이 찍힌 참가자 = 이미 확정이 끝난 참가자다(deleted_at이 곧 종료 표시)
    private static RunningPlayer player(RunningPlayerStatus status, LocalDateTime deletedAt) {
        return RunningPlayer.builder()
                .runningPlayerId(PLAYER_ID)
                .userId(USER_ID)
                .status(status)
                .avgPace(AVG_PACE)
                .targetDistance(TARGET)
                .startAt(PAST)
                .deletedAt(deletedAt)
                .build();
    }

    private static RunningRoom room(RunningRoomType type, Integer targetDistance) {
        return room(type, targetDistance, RunningRoomStatus.STARTED);
    }

    private static RunningRoom room(RunningRoomType type, Integer targetDistance,
                                    RunningRoomStatus status) {
        return room(type, targetDistance, status,
                type == RunningRoomType.SOLO ? 1 : 2);
    }

    private static RunningRoom room(RunningRoomType type, Integer targetDistance,
                                    RunningRoomStatus status, int currentPlayerCount) {
        return RunningRoom.builder()
                .runningRoomId(ROOM_ID)
                .type(type)
                .status(status)
                // 종료 상태는 닫힌 시각이 있어야 복원된다
                .closeAt(status.isTerminal() ? PAST.plusMinutes(1) : null)
                .startAt(PAST)
                .targetDistance(targetDistance)
                .avgPace(AVG_PACE)
                .currentPlayerCount(currentPlayerCount)
                .maxPlayerCount(type == RunningRoomType.SOLO ? 1 : 4)
                .sessions(List.of(new SessionDraft(
                        new UserId(USER_ID), new RunningPlayerId(PLAYER_ID), 0, true)))
                .build();
    }

    // 북쪽으로 초당 stepMeters씩 달리는 트랙 — (count - 1) * step 미터가 실측 거리다
    private static RunningTrack track(int count, double stepMeters) {
        return track(count, stepMeters, CADENCE);
    }

    // 케이던스는 기기가 안 주면 null이다 — 그때 기록의 avg_cadence도 비어야 한다
    private static RunningTrack track(int count, double stepMeters, Integer cadenceSpm) {
        List<TrackPoint> points = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            points.add(new TrackPoint(i, 37.5 + i * stepMeters / METERS_PER_DEGREE, 127.0,
                    null, 5.0, null, null, cadenceSpm, null, TRACK_START.plusSeconds(i)));
        }
        return new RunningTrack("raw", points);
    }

    private void givenPlayer(RunningPlayer player) {
        given(loadRoomPlayerPort.load(new RunningRoomId(ROOM_ID), new UserId(USER_ID)))
                .willReturn(Optional.ofNullable(player));
    }

    private void givenRoom(RunningRoom room) {
        given(loadRunningRoomPort.loadById(new RunningRoomId(ROOM_ID))).willReturn(Optional.of(room));
    }

    private void givenTrack(RunningTrack track) {
        given(loadUserWeightPort.loadWeightKg(new UserId(USER_ID))).willReturn(Optional.of(WEIGHT));
        given(loadRunningTrackPort.load(ROOM_ID, new UserId(USER_ID))).willReturn(track);
        // 기록을 만들 수 있는 트랙일 때만 업로드·날씨를 탄다 — 빈 트랙에 미리 깔면 미사용 스텁이 된다
        if (!track.isEmpty()) {
            given(saveGpsTrackPort.save(any())).willReturn(GPS_TRACK_KEY);
            given(loadWeatherPort.load(anyDouble(), anyDouble(), any())).willReturn(WEATHER);
        }
    }

    private RunningRecord savedRecord() {
        verify(createRunningRecordPort).create(recordCaptor.capture());
        return recordCaptor.getValue();
    }

    private void finish() {
        handler.handle(new FinishRunningCommand(ROOM_ID, USER_ID, false));
    }

    @Nested
    @DisplayName("멱등 테스트")
    class IdempotencyTest {

        @Test
        @DisplayName("이미 완주 확정된 참가자는 다시 확정하지 않고 넘어간다")
        void skipsAlreadyCompletedPlayer() {
            // given -> 앞선 요청이나 타임아웃이 먼저 확정해 둔 상태
            givenPlayer(player(RunningPlayerStatus.COMPLETED, PAST.plusMinutes(20)));

            // when & then -> 도메인 상태 전이를 다시 시도하면 500이 된다
            assertThatCode(FinishRunningHandlerTest.this::finish).doesNotThrowAnyException();
            verifyNoInteractions(updateRunningPlayerPort);
        }

        @Test
        @DisplayName("이미 이탈 확정된 참가자도 같은 경로를 탄다")
        void skipsAlreadyLeftPlayer() {
            // given
            givenPlayer(player(RunningPlayerStatus.RUNNING_LEFT_PENALTY, PAST.plusMinutes(10)));

            // when & then
            assertThatCode(FinishRunningHandlerTest.this::finish).doesNotThrowAnyException();
            verifyNoInteractions(updateRunningPlayerPort);
        }

        @Test
        @DisplayName("확정을 건너뛰어도 로컬 트랙 정리를 위해 버퍼는 비운다")
        void deletesTrackOnRepeatedFinish() {
            // given
            givenPlayer(player(RunningPlayerStatus.COMPLETED, PAST.plusMinutes(20)));

            // when -> 클라가 ack를 못 받아 다시 보낸 상황
            finish();

            // then -> 여기서 안 지우면 재전송 클라의 버퍼가 TTL까지 남는다
            verify(deleteRunningTrackPort).delete(ROOM_ID, new UserId(USER_ID));
        }
    }

    @Nested
    @DisplayName("상태 확정 테스트")
    class StatusTest {

        @Test
        @DisplayName("목표 거리를 채우면 완주다")
        void completesWhenTargetReached() {
            // given -> 약 5,040m를 뛰어 목표를 넘겼다
            RunningPlayer player = player(RunningPlayerStatus.RUNNING, null);
            givenPlayer(player);
            givenRoom(room(RunningRoomType.MATCH, TARGET));
            givenTrack(track(1_801, 2.8));

            // when
            finish();

            // then
            assertThat(player.getStatus()).isEqualTo(RunningPlayerStatus.COMPLETED);
            assertThat(player.getDeletedAt()).isPresent();
        }

        @Test
        @DisplayName("목표의 80%를 채운 조기 종료는 페널티가 없다")
        void leavesWithoutPenaltyAtRatioBoundary() {
            // given -> 4,002.5m를 뛰어 구간 경계가 정확히 4,000m에서 끊긴다 = 목표의 80%
            RunningPlayer player = player(RunningPlayerStatus.RUNNING, null);
            givenPlayer(player);
            givenRoom(room(RunningRoomType.MATCH, TARGET));
            givenTrack(track(1_602, 2.5));

            // when
            finish();

            // then -> 경계값은 페널티가 아니다(비율 미만일 때만 제재)
            assertThat(player.getStatus()).isEqualTo(RunningPlayerStatus.RUNNING_LEFT_NO_PENALTY);
        }

        @Test
        @DisplayName("목표의 80%에 못 미치면 페널티가 붙는다")
        void leavesWithPenaltyBelowRatio() {
            // given -> 3,992.5m를 뛰어 3,990m에서 끊긴다 = 79.8%로 경계 바로 아래
            RunningPlayer player = player(RunningPlayerStatus.RUNNING, null);
            givenPlayer(player);
            givenRoom(room(RunningRoomType.MATCH, TARGET));
            givenTrack(track(1_598, 2.5));

            // when
            finish();

            // then
            assertThat(player.getStatus()).isEqualTo(RunningPlayerStatus.RUNNING_LEFT_PENALTY);
        }

        @Test
        @DisplayName("제재 대상이면 매칭 쿨다운을 건다")
        void startsMatchCooldownOnPenalty() {
            // given -> 3,990m = 79.8%로 경계 바로 아래. 동료가 있는 방이다
            givenPlayer(player(RunningPlayerStatus.RUNNING, null));
            givenRoom(room(RunningRoomType.MATCH, TARGET));
            givenTrack(track(1_598, 2.5));

            // when
            finish();

            // then -> 근거는 status에 남고 "지금 막혀 있나"는 Redis TTL이 답한다.
            //         기간은 취소 이탈과 따로 설정한다(running-finish.cooldown)
            verify(startMatchCooldownPort).start(new UserId(USER_ID), COOLDOWN);
        }

        @Test
        @DisplayName("1인 확정 방에서는 비율이 미달이어도 제재하지 않는다")
        void exemptsSoleRunnerFromPenalty() {
            // given -> 아무도 안 붙어 혼자 확정된 방. 곤란해지는 상대가 없다(feature-spec).
            //          러닝 시작 후 인원은 줄지 않으므로 이 값이 곧 확정 시점 인원이다
            RunningPlayer player = player(RunningPlayerStatus.RUNNING, null);
            givenPlayer(player);
            givenRoom(room(RunningRoomType.MATCH, TARGET, RunningRoomStatus.STARTED, 1));
            givenTrack(track(1_598, 2.5));

            // when
            finish();

            // then
            assertThat(player.getStatus()).isEqualTo(RunningPlayerStatus.RUNNING_LEFT_NO_PENALTY);
            verifyNoInteractions(startMatchCooldownPort);
        }

        @Test
        @DisplayName("완주하면 쿨다운을 걸지 않는다")
        void doesNotStartCooldownOnComplete() {
            // given -> 목표를 채운 러닝
            givenPlayer(player(RunningPlayerStatus.RUNNING, null));
            givenRoom(room(RunningRoomType.MATCH, TARGET));
            givenTrack(track(2_001, 2.5));

            // when
            finish();

            // then
            verifyNoInteractions(startMatchCooldownPort);
        }

        @Test
        @DisplayName("기록을 만들 수 없는 트랙이면 실제 거리를 0으로 판정한다")
        void treatsUnusableTrackAsZeroDistance() {
            // given -> 좌표가 하나도 안 올라온 러닝
            RunningPlayer player = player(RunningPlayerStatus.RUNNING, null);
            givenPlayer(player);
            givenRoom(room(RunningRoomType.MATCH, TARGET));
            givenTrack(new RunningTrack("", List.of()));

            // when -> 상태는 확정한다. 기록만 남기지 않는다
            finish();

            // then
            assertThat(player.getStatus()).isEqualTo(RunningPlayerStatus.RUNNING_LEFT_PENALTY);
        }

        @Test
        @DisplayName("목표가 없는 솔로 러닝은 사용자가 끝내면 완주다")
        void completesSoloWithoutTarget() {
            // given -> 솔로 방은 target_distance가 null이라 비율을 잴 분모가 없다
            RunningPlayer player = player(RunningPlayerStatus.RUNNING, null);
            givenPlayer(player);
            givenRoom(room(RunningRoomType.SOLO, null));
            givenTrack(track(1_601, 2.5));

            // when
            finish();

            // then
            assertThat(player.getStatus()).isEqualTo(RunningPlayerStatus.COMPLETED);
        }

        @Test
        @DisplayName("확정한 참가자는 되돌려 쓰고 트랙 버퍼를 비운다")
        void writesBackPlayerAndClearsTrack() {
            // given
            RunningPlayer player = player(RunningPlayerStatus.RUNNING, null);
            givenPlayer(player);
            givenRoom(room(RunningRoomType.MATCH, TARGET));
            givenTrack(track(1_801, 2.8));

            // when
            finish();

            // then -> 이 호출이 빠지면 상태 전이가 통째로 사라진다
            verify(updateRunningPlayerPort).update(player);
            verify(deleteRunningTrackPort).delete(ROOM_ID, new UserId(USER_ID));
        }
    }

    @Nested
    @DisplayName("기록 저장 테스트")
    class RecordTest {

        // 각 케이스는 같은 러닝(목표를 넘겨 뛴 5,040m)에서 한 측면씩만 본다
        private RunningRecord finishAndCapture(RunningTrack track) {
            givenPlayer(player(RunningPlayerStatus.RUNNING, null));
            givenRoom(room(RunningRoomType.MATCH, TARGET));
            givenTrack(track);
            finish();
            return savedRecord();
        }

        @Test
        @DisplayName("확정 지표가 그대로 기록이 된다")
        void savesConfirmedMetrics() {
            // when
            RunningRecord record = finishAndCapture(track(1_801, 2.8));

            // then -> 목표를 넘겨 뛰었으므로 목표 지점에서 끊긴다
            assertThat(record.getTotalDistance().meters()).isEqualTo(TARGET);
            assertThat(record.getSplits()).hasSize(TARGET / PROPERTIES.splitDistanceMeters());
            assertThat(record.getRoutePolyline().value()).isNotBlank();
            assertThat(record.getGpsTrackKey().value()).isEqualTo(GPS_TRACK_KEY);
        }

        @Test
        @DisplayName("총 시간은 구간 시간의 합이다")
        void totalDurationEqualsSumOfSplits() {
            // when
            RunningRecord record = finishAndCapture(track(1_801, 2.8));

            // then -> total_duration은 구간 duration의 합이다
            assertThat(record.getTotalDuration().seconds()).isEqualTo(
                    record.getSplits().stream()
                            .mapToInt(split -> split.getDuration().seconds())
                            .sum());
        }

        @Test
        @DisplayName("칼로리는 구간 합이 아니라 확정 거리·시간으로 낸다")
        void caloriesComeFromConfirmedMetrics() {
            // when
            RunningRecord record = finishAndCapture(track(1_801, 2.8));

            // then -> 구간 합은 구간마다 반올림돼 총합과 어긋난다
            assertThat(record.getTotalCalories().kcal()).isEqualTo(CalorieCalculator.kcal(
                    record.getAvgPace().secondsPerKm(),
                    record.getTotalDuration().seconds(),
                    WEIGHT));
        }

        @Test
        @DisplayName("케이던스는 구간 시간으로 가중 평균한다")
        void avgCadenceIsDurationWeighted() {
            // given -> 모든 좌표가 같은 케이던스면 가중해도 그 값이다
            RunningRecord record = finishAndCapture(track(1_801, 2.8));

            // then
            assertThat(record.getAvgCadence()).isPresent();
            assertThat(record.getAvgCadence().orElseThrow().stepsPerMinute()).isEqualTo(CADENCE);
        }

        @Test
        @DisplayName("케이던스를 주지 않는 기기면 비워 둔다")
        void avgCadenceIsEmptyWithoutSensor() {
            // given -> 선택 항목이라 표본이 없으면 null이다
            RunningRecord record = finishAndCapture(track(1_801, 2.8, null));

            // then
            assertThat(record.getAvgCadence()).isEmpty();
        }

        @Test
        @DisplayName("날씨는 출발 지점·출발 시각으로 조회한다")
        void loadsWeatherAtOrigin() {
            // when
            RunningRecord record = finishAndCapture(track(1_801, 2.8));

            // then
            verify(loadWeatherPort).load(37.5, 127.0, record.getPeriod().startAt());
            assertThat(record.getWeatherCode().value()).isEqualTo(WEATHER.code());
            assertThat(record.getTemperature().celsius()).isEqualByComparingTo(WEATHER.temperature());
        }

        @Test
        @DisplayName("S3에는 압축 원본을 그대로 올린다")
        void uploadsRawTrack() {
            // when
            RunningRecord record = finishAndCapture(track(1_801, 2.8));

            // then -> 목표 이후 좌표까지 남기는 것이 원본 트랙의 역할이다
            verify(saveGpsTrackPort).save(uploadCaptor.capture());
            GpsTrackUpload upload = uploadCaptor.getValue();
            assertThat(upload.raw()).isEqualTo("raw");
            assertThat(upload.runningRoomId()).isEqualTo(ROOM_ID);
            assertThat(upload.userId()).isEqualTo(USER_ID);
            assertThat(upload.startAt()).isEqualTo(record.getPeriod().startAt());
            assertThat(upload.endAt()).isEqualTo(record.getPeriod().endAt());
        }

        @Test
        @DisplayName("기록을 만들 수 없는 트랙이면 업로드도 저장도 하지 않는다")
        void skipsRecordWhenTrackUnusable() {
            // given -> 좌표가 하나도 안 올라온 러닝
            givenPlayer(player(RunningPlayerStatus.RUNNING, null));
            givenRoom(room(RunningRoomType.MATCH, TARGET));
            givenTrack(new RunningTrack("", List.of()));

            // when
            finish();

            // then -> 상태만 확정하고 기록은 남기지 않는다
            verifyNoInteractions(saveGpsTrackPort, loadWeatherPort, createRunningRecordPort);
        }

        @Test
        @DisplayName("구간은 1번부터 이어지고 경로 인덱스가 맞물린다")
        void splitsAreSequential() {
            // when
            RunningRecord record = finishAndCapture(track(1_801, 2.8));

            // then -> 도메인이 검증하는 불변이라 여기서 깨지면 저장 자체가 막힌다
            List<RunningSplit> splits = record.getSplits();
            assertThat(splits.get(0).getSplitNumber().value()).isEqualTo(1);
            assertThat(splits.get(0).getRouteRange().startIndex()).isZero();
            assertThat(splits.get(splits.size() - 1).getRouteRange().endIndex())
                    .isEqualTo(splits.size());
        }
    }

    @Nested
    @DisplayName("방 마감 테스트")
    class CloseRoomTest {

        private RunningRoom finishIn(RunningRoom room) {
            givenPlayer(player(RunningPlayerStatus.RUNNING, null));
            givenRoom(room);
            givenTrack(track(1_801, 2.8));
            finish();
            return room;
        }

        // 러닝이 끝나면 자리를 비운다 — 인원은 그대로 두고 is_connected만 내린다(erd)
        private RoomSession sessionOf(RunningRoom room) {
            return room.getSessions().stream()
                    .filter(session -> session.isSameUser(new UserId(USER_ID)))
                    .findFirst()
                    .orElseThrow();
        }

        @Test
        @DisplayName("아직 뛰는 참가자가 있으면 방을 닫지 않는다")
        void keepsRoomOpenWhileOthersRun() {
            // given -> 4인 방에서 나만 먼저 끝냈다
            given(existsRunningPlayerPort.existsRunning(new RunningRoomId(ROOM_ID)))
                    .willReturn(true);

            // when
            RunningRoom room = finishIn(room(RunningRoomType.MATCH, TARGET));

            // then -> 방은 열려 있지만 내 세션은 끊겨 저장돼야 한다
            assertThat(room.getStatus()).isEqualTo(RunningRoomStatus.STARTED);
            assertThat(sessionOf(room).isConnected()).isFalse();
            verify(updateRunningRoomPort).update(room);
        }

        @Test
        @DisplayName("전원이 종료되면 방이 닫힌다")
        void closesRoomWhenLastPlayerFinishes() {
            // given -> existsRunning이 false = 남은 사람이 없다

            // when
            RunningRoom room = finishIn(room(RunningRoomType.MATCH, TARGET));

            // then -> 상태와 닫힌 시각이 함께 확정된다
            assertThat(room.getStatus()).isEqualTo(RunningRoomStatus.FINISHED);
            assertThat(room.getCloseAt()).isPresent();
            verify(updateRunningRoomPort).update(room);
        }

        @Test
        @DisplayName("혼자 뛴 방도 CANCELLED가 아니라 FINISHED다")
        void closesSoloRoomAsFinished() {
            // when
            RunningRoom room = finishIn(room(RunningRoomType.SOLO, null));

            // then -> CANCELLED로 닫으면 terminal이라 결과 조회 경로가 무너진다
            assertThat(room.getStatus()).isEqualTo(RunningRoomStatus.FINISHED);
        }

        @Test
        @DisplayName("강제 종료가 먼저 닫은 방은 다시 건드리지 않는다")
        void skipsAlreadyClosedRoom() {
            // given -> 참가자는 아직 RUNNING인데 방만 닫혀 있는 상태
            RunningRoom room = finishIn(
                    room(RunningRoomType.MATCH, TARGET, RunningRoomStatus.FINISHED));

            // then -> finish()를 다시 부르면 도메인 예외라 남은 사람을 세지도 않는다.
            //         다만 세션 종료는 저장돼야 한다
            assertThat(room.getStatus()).isEqualTo(RunningRoomStatus.FINISHED);
            assertThat(sessionOf(room).isConnected()).isFalse();
            verifyNoInteractions(existsRunningPlayerPort);
            verify(updateRunningRoomPort).update(room);
        }

        @Test
        @DisplayName("기록이 하나도 없는 방은 CANCELLED로 닫는다")
        void cancelsRoomWithoutAnyRecord() {
            // given -> 시작만 눌렀거나 몇십 미터 만에 그만둬 아무도 유효 러닝 판정을 통과하지 못했다
            given(existsRunningRecordPort.existsInRoom(new RunningRoomId(ROOM_ID)))
                    .willReturn(false);

            // when
            RunningRoom room = finishIn(room(RunningRoomType.MATCH, TARGET));

            // then -> 완료로 남기지 않는다. 닫힌 시각은 FINISHED와 같게 함께 찍힌다
            assertThat(room.getStatus()).isEqualTo(RunningRoomStatus.CANCELLED);
            assertThat(room.getCloseAt()).isPresent();
        }

        @Test
        @DisplayName("참가자 갱신을 반영한 뒤에 남은 사람을 센다")
        void countsRunnersAfterPlayerUpdate() {
            // when
            finishIn(room(RunningRoomType.MATCH, TARGET));

            // then -> 순서가 뒤집히면 방금 끝낸 자신이 RUNNING으로 잡혀 혼자 뛴 방이 안 닫힌다
            InOrder order = inOrder(updateRunningPlayerPort, existsRunningPlayerPort);
            order.verify(updateRunningPlayerPort).update(any());
            order.verify(existsRunningPlayerPort).existsRunning(new RunningRoomId(ROOM_ID));
        }
    }

    @Nested
    @DisplayName("거부 테스트")
    class RejectTest {

        @Test
        @DisplayName("이 방의 참가자가 아니면 거부한다")
        void rejectNonRoomPlayer() {
            // given -> 남의 방 번호를 실어 보냈다
            givenPlayer(null);

            // when & then
            assertThatThrownBy(FinishRunningHandlerTest.this::finish)
                    .isInstanceOf(NotRoomPlayerException.class);
            verifyNoInteractions(updateRunningPlayerPort, deleteRunningTrackPort);
        }

        @Test
        @DisplayName("RUNNING_START를 거치지 않은 참가자는 확정할 러닝이 없다")
        void rejectNotStartedPlayer() {
            // given -> 배정만 받고 시작 메시지는 보내지 않았다
            givenPlayer(player(RunningPlayerStatus.JOINED, null));

            // when & then -> 도메인 예외로 새면 500이라 여기서 거른다
            assertThatThrownBy(FinishRunningHandlerTest.this::finish)
                    .isInstanceOf(RunningNotStartableException.class);
            verifyNoInteractions(updateRunningPlayerPort, deleteRunningTrackPort);
        }

        @Test
        @DisplayName("수락하지 않은 초대도 종료 대상이 아니다")
        void rejectInvitedPlayer() {
            // given
            givenPlayer(player(RunningPlayerStatus.INVITED, null));

            // when & then
            assertThatThrownBy(FinishRunningHandlerTest.this::finish)
                    .isInstanceOf(RunningNotStartableException.class);
            verifyNoInteractions(updateRunningPlayerPort, deleteRunningTrackPort);
        }

        @Test
        @DisplayName("참가자는 있는데 방이 없으면 거부한다")
        void rejectUnknownRoom() {
            // given -> 목표 거리를 정하는 쪽이 방이라 방 없이는 판정할 수 없다
            givenPlayer(player(RunningPlayerStatus.RUNNING, null));
            given(loadRunningRoomPort.loadById(new RunningRoomId(ROOM_ID)))
                    .willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(FinishRunningHandlerTest.this::finish)
                    .isInstanceOf(RunningRoomNotFoundException.class);
            verifyNoInteractions(updateRunningPlayerPort, deleteRunningTrackPort);
        }

        @Test
        @DisplayName("체중이 없으면 온보딩을 마치지 않은 사용자다")
        void rejectWithoutWeight() {
            // given -> 온보딩은 몸무게가 필수라 여기까지 오면 데이터가 어긋난 것이다
            givenPlayer(player(RunningPlayerStatus.RUNNING, null));
            givenRoom(room(RunningRoomType.MATCH, TARGET));
            given(loadUserWeightPort.loadWeightKg(new UserId(USER_ID))).willReturn(Optional.empty());

            // when & then -> 칼로리를 지어내느니 확정을 멈춘다
            assertThatThrownBy(FinishRunningHandlerTest.this::finish)
                    .isInstanceOf(OnboardingNotCompletedException.class);
            verifyNoInteractions(updateRunningPlayerPort, deleteRunningTrackPort);
        }
    }

    @Nested
    @DisplayName("평균 페이스 갱신 테스트")
    class AvgPaceUpdateTest {

        // 핸들러의 AVG_PACE_SAMPLE_SIZE와 같아야 한다 — 어긋나면 스텁이 안 물린다
        private static final int SAMPLE_SIZE = 5;

        // 조회 결과에는 방금 만든 기록도 포함된다 — 어댑터가 같은 트랜잭션에서 다시 읽기 때문이다
        private void givenRecent(RecentRunningPace... recent) {
            given(loadRecentRunningPacesPort.loadRecent(new UserId(USER_ID), SAMPLE_SIZE))
                    .willReturn(List.of(recent));
        }

        private static RecentRunningPace pace(int meters, int seconds) {
            return new RecentRunningPace(meters, seconds);
        }

        // 기록이 남는 평범한 완주 — 갱신은 이 경로에서만 일어난다
        private void finishWithRecord() {
            givenPlayer(player(RunningPlayerStatus.RUNNING, null));
            givenRoom(room(RunningRoomType.MATCH, TARGET));
            givenTrack(track(1_801, 2.8));
            finish();
        }

        @Test
        @DisplayName("최근 기록이 5건에 못 미치면 갱신하지 않는다")
        void skipsUpdateBelowSampleSize() {
            // given -> 방금 만든 기록까지 네 건뿐이다
            givenRecent(pace(5_000, 1_500), pace(5_000, 1_500),
                    pace(5_000, 1_500), pace(5_000, 1_500));

            // when
            finishWithRecord();

            // then -> 한두 번의 실측으로 갈아치우면 컨디션 나쁜 하루가 실력이 된다.
            //         온보딩 입력값을 그대로 둔다
            verifyNoInteractions(updateUserAvgPacePort);
        }

        @Test
        @DisplayName("5건이 차면 거리 합과 시간 합으로 다시 낸다")
        void updatesFromRecentFive() {
            // given -> 21,000m를 6,400초에 뛰었다 → 304초/km
            givenRecent(pace(10_000, 3_000), pace(5_000, 1_500),
                    pace(3_000, 900), pace(2_000, 600), pace(1_000, 400));

            // when
            finishWithRecord();

            // then -> 기록별 페이스의 산술 평균은 320초다. 짧은 1km가 값을 끌고 가지 않는다
            verify(updateUserAvgPacePort).updateAvgPace(new UserId(USER_ID), new AvgPace(304));
        }

        @Test
        @DisplayName("온보딩 페이스 범위를 넘기면 경계로 맞춘다")
        void clampsToOnboardingRange() {
            // given -> 다섯 건 모두 2,000초/km. 기록 페이스는 3,600까지 허용하지만
            //          user_onboardings.avg_pace는 1,800이 상한이다
            givenRecent(pace(1_000, 2_000), pace(1_000, 2_000), pace(1_000, 2_000),
                    pace(1_000, 2_000), pace(1_000, 2_000));

            // when
            finishWithRecord();

            // then -> 갱신을 포기하지 않고 경계를 적는다. 그대로 넣으면 CHECK 제약에 걸린다
            verify(updateUserAvgPacePort).updateAvgPace(new UserId(USER_ID), new AvgPace(1_800));
        }

        @Test
        @DisplayName("기록을 만들지 못한 종료는 조회조차 하지 않는다")
        void skipsEntirelyWithoutRecord() {
            // given -> 좌표가 하나도 안 올라온 러닝 — 표본이 늘지 않았다
            givenPlayer(player(RunningPlayerStatus.RUNNING, null));
            givenRoom(room(RunningRoomType.MATCH, TARGET));
            givenTrack(new RunningTrack("", List.of()));

            // when
            finish();

            // then
            verifyNoInteractions(loadRecentRunningPacesPort, updateUserAvgPacePort);
        }
    }
}
