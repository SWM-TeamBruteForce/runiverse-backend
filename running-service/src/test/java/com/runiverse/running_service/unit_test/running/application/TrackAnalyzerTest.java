package com.runiverse.running_service.unit_test.running.application;

import com.runiverse.running_service.application.running.common.PolylineDecoder;
import com.runiverse.running_service.application.running.common.RunningFinishProperties;
import com.runiverse.running_service.application.running.common.TrackAnalysis;
import com.runiverse.running_service.application.running.common.TrackAnalyzer;
import com.runiverse.running_service.application.running.common.TrackFilter;
import com.runiverse.running_service.application.running.port.out.TrackPoint;
import com.runiverse.running_service.domain.running.record.RunningRecord;
import com.runiverse.running_service.domain.running.record.SegmentDraft;
import com.runiverse.running_service.domain.running.record.SplitDraft;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.runiverse.running_service.support.TrackFilterFixtures.DEFAULT_PROPERTIES;
import static com.runiverse.running_service.support.TrackFilterFixtures.unfiltered;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

@DisplayName("트랙 분석 단위 테스트")
public class TrackAnalyzerTest {

    private static final LocalDateTime START = LocalDateTime.of(2026, 8, 27, 19, 0, 0);
    private static final int TARGET = 5_000;
    private static final double METERS_PER_DEGREE = 111_320.0;
    private static final BigDecimal WEIGHT = new BigDecimal("70.0");
    private static final UUID USER_ID = UUID.fromString("01a02344-364f-7d53-860a-c4f967cf1dbd");

    // 조기 종료 제재로 매칭 신청이 막히는 기간 — 이 테스트의 주제는 아니다
    private static final Duration COOLDOWN = Duration.ofMinutes(20);
    private static final RunningFinishProperties PROPERTIES = new RunningFinishProperties(
            0.8, 10, 100, 60, 3.0, COOLDOWN);

    private static TrackPoint point(long sequence, double latitude, Double altitude,
                                    LocalDateTime at) {
        return new TrackPoint(sequence, latitude, 127.0, altitude, 5.0, null, null, 168, null, at, 0);
    }

    // 북쪽으로 초당 stepMeters씩 달리는 트랙
    private static List<TrackPoint> track(int count, double stepMeters, Double altitude) {
        List<TrackPoint> points = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            points.add(point(i, 37.5 + i * stepMeters / METERS_PER_DEGREE, altitude,
                    START.plusSeconds(i)));
        }
        return points;
    }

    private static TrackAnalysis analyze(List<TrackPoint> points) {
        return TrackAnalyzer.analyze(unfiltered(points), TARGET, WEIGHT, PROPERTIES).orElseThrow();
    }

    @Test
    @DisplayName("목표를 넘겨 뛰면 총거리가 목표로 확정된다")
    void totalDistanceIsCappedAtTarget() {
        // given -> 약 5,040m
        TrackAnalysis analysis = analyze(track(1_800, 2.8, null));

        // then
        assertThat(analysis.totalDistanceMeters()).isEqualTo(TARGET);
        assertThat(analysis.splits()).hasSize(TARGET / PROPERTIES.splitDistanceMeters());
    }

    @Test
    @DisplayName("총 시간은 구간 시간의 합과 정확히 같다")
    void totalDurationEqualsSumOfSplits() {
        // given
        TrackAnalysis analysis = analyze(track(1_800, 2.8, null));

        // then -> total_duration은 구간 duration의 합이다
        assertThat(analysis.totalDurationSeconds())
                .isEqualTo(analysis.splits().stream().mapToInt(SplitDraft::duration).sum());
    }

    @Test
    @DisplayName("기록 기간이 첫 구간 시작과 마지막 구간 끝을 그대로 쓴다")
    void periodComesFromSplits() {
        // given
        TrackAnalysis analysis = analyze(track(1_800, 2.8, null));

        // then -> 경계점의 보간 시각을 쓰면 구간 기간을 못 감싸 검증에서 걸린다
        List<SplitDraft> splits = analysis.splits();
        assertThat(analysis.startAt()).isEqualTo(splits.get(0).startAt());
        assertThat(analysis.endAt()).isEqualTo(splits.get(splits.size() - 1).endAt());
    }

    @Test
    @DisplayName("폴리라인은 경계점 수만큼의 좌표를 담는다")
    void polylineCoversEveryBoundary() {
        // given
        TrackAnalysis analysis = analyze(track(1_800, 2.8, null));

        // then -> 구간 인덱스가 이 배열의 위치를 가리키므로 개수가 어긋나면 구간이 깨진다
        String polyline = analysis.segments().getFirst().routePolyline();
        assertThat(polyline).isNotBlank();
        assertThat(countPolylinePoints(polyline)).isEqualTo(analysis.splits().size() + 1);
    }

    @Test
    @DisplayName("평평한 고도는 노이즈로 걸러 누적 상승이 0이다")
    void flatTrackHasNoElevationGain() {
        // when
        TrackAnalysis analysis = analyze(track(1_800, 2.8, 18.4));

        // then -> GPS 수직 오차를 상승으로 세면 평지에서도 고도가 쌓인다
        assertThat(analysis.totalElevationGainMeters()).isZero();
    }

    @Test
    @DisplayName("고도를 못 잰 트랙은 누적 상승이 null이다")
    void missingAltitudeLeavesElevationNull() {
        // when
        TrackAnalysis analysis = analyze(track(1_800, 2.8, null));

        // then
        assertThat(analysis.totalElevationGainMeters()).isNull();
    }

    @Test
    @DisplayName("임계값을 넘는 상승만 누적한다")
    void accumulatesOnlyRisesAboveThreshold() {
        // given -> 처음 190점은 초당 5m씩 오르막, 나머지는 평지 — 절단 위치와 무관하게 임계값만 본다
        List<TrackPoint> points = new ArrayList<>();
        for (int i = 0; i < 200; i++) {
            double altitude = i < 190 ? 10.0 + i * 5.0 : 10.0 + 189 * 5.0;
            points.add(point(i, 37.5 + i * 2.8 / METERS_PER_DEGREE, altitude,
                    START.plusSeconds(i)));
        }

        // when
        TrackAnalysis analysis = analyze(points);

        // then -> 189번 오르며 5m씩 = 945m
        assertThat(analysis.totalElevationGainMeters()).isEqualTo(945);
    }

    @Test
    @DisplayName("좌표 사이 상승이 임계값보다 작은 완만한 오르막도 누적한다")
    void accumulatesGentleClimbAcrossManyPoints() {
        // given -> 처음 190점은 초당 0.2m씩 오르막(총 37.8m), 나머지는 평지
        List<TrackPoint> points = new ArrayList<>();
        for (int i = 0; i < 200; i++) {
            double altitude = i < 190 ? 10.0 + i * 0.2 : 10.0 + 189 * 0.2;
            points.add(point(i, 37.5 + i * 2.8 / METERS_PER_DEGREE, altitude,
                    START.plusSeconds(i)));
        }

        // when
        TrackAnalysis analysis = analyze(points);

        // then -> 바로 앞 좌표와만 비교하면 0.2m씩이라 전부 노이즈로 걸려 0이 된다.
        //         임계값(3m)을 넘을 때마다 3.2m씩 11번 = 35m, 남은 2.6m는 임계값 아래라 세지 않는다
        assertThat(analysis.totalElevationGainMeters()).isEqualTo(35);
    }

    @Test
    @DisplayName("누적 상승은 마지막 경계까지만 센다")
    void elevationGainStopsAtLastBoundary() {
        // given -> 총 ~557m라 기록은 550m 경계에서 끝난다. 경계 안(190~195)과
        //          꼬리(198~)에 오르막을 하나씩 두면 절단이 어느 쪽으로 어긋나도 걸린다
        List<TrackPoint> points = new ArrayList<>();
        for (int i = 0; i < 200; i++) {
            double altitude;
            if (i < 190) {
                altitude = 10.0;
            } else if (i < 196) {
                altitude = 10.0 + (i - 189) * 5.0;   // 경계 안 오르막 +30m
            } else if (i < 198) {
                altitude = 40.0;
            } else {
                altitude = 40.0 + (i - 197) * 5.0;   // 꼬리 오르막 +10m
            }
            points.add(point(i, 37.5 + i * 2.8 / METERS_PER_DEGREE, altitude,
                    START.plusSeconds(i)));
        }

        // when
        TrackAnalysis analysis = analyze(points);

        // then -> 경계 안 상승 30m만 남고 꼬리 상승 10m는 기록 밖이다
        assertThat(analysis.totalDistanceMeters()).isEqualTo(550);
        assertThat(analysis.totalElevationGainMeters()).isEqualTo(30);
    }

    @Test
    @DisplayName("목표 이후의 상승은 누적 상승에 넣지 않는다")
    void elevationGainExcludesClimbBeyondTarget() {
        // given -> 목표(5,000m)까지는 평지, 목표를 확실히 지난 지점부터 가파르게 오른다.
        //          명목 거리(i×2.8)와 실측 거리의 오차가 경계를 넘지 않게 +20m 여유를 둔다
        List<TrackPoint> points = new ArrayList<>();
        for (int i = 0; i < 1_800; i++) {
            double meters = i * 2.8;
            double altitude = meters < TARGET + 20 ? 10.0 : 10.0 + (meters - TARGET - 20) * 2.0;
            points.add(point(i, 37.5 + meters / METERS_PER_DEGREE, altitude,
                    START.plusSeconds(i)));
        }

        // when
        TrackAnalysis analysis = analyze(points);

        // then -> 거리·시간·경로처럼 목표 지점에서 끊는다. 넘긴 뒤의 오르막은 기록 밖이다
        assertThat(analysis.totalDistanceMeters()).isEqualTo(TARGET);
        assertThat(analysis.totalElevationGainMeters()).isZero();
    }

    @Test
    @DisplayName("최소 거리를 못 넘기면 기록을 만들지 않는다")
    void tooShortDistanceProducesNoAnalysis() {
        // given -> 약 50m (최소 100m 미만)
        List<TrackPoint> points = track(20, 2.8, null);

        // when & then -> 기록 없이 상태만 확정하는 경로다
        assertThat(TrackAnalyzer.analyze(unfiltered(points), TARGET, WEIGHT, PROPERTIES)).isEmpty();
    }

    @Test
    @DisplayName("최소 시간을 못 넘기면 기록을 만들지 않는다")
    void tooShortDurationProducesNoAnalysis() {
        // given -> 300m를 30초에 (거리는 넘지만 시간이 60초 미만)
        List<TrackPoint> points = track(30, 10.0, null);

        // when & then
        assertThat(TrackAnalyzer.analyze(unfiltered(points), TARGET, WEIGHT, PROPERTIES)).isEmpty();
    }

    @Test
    @DisplayName("페이스가 기록 범위를 벗어나면 기록을 만들지 않는다")
    void impossiblePaceProducesNoAnalysis() {
        // given -> 200m를 2,000초에. 페이스 10,000 초/km라 Pace(MAX 3600) 밖이다
        List<TrackPoint> points = new ArrayList<>();
        for (int i = 0; i < 200; i++) {
            points.add(point(i, 37.5 + i * 1.0 / METERS_PER_DEGREE, null,
                    START.plusSeconds(i * 10L)));
        }

        // when & then -> 여기서 안 거르면 RunningRecord 생성에서 터져 기록이 통째로 사라진다
        assertThat(TrackAnalyzer.analyze(unfiltered(points), TARGET, WEIGHT, PROPERTIES)).isEmpty();
    }

    @Test
    @DisplayName("좌표가 없으면 기록을 만들지 않는다")
    void emptyTrackProducesNoAnalysis() {
        // when & then
        assertThat(TrackAnalyzer.analyze(unfiltered(List.of()), TARGET, WEIGHT, PROPERTIES)).isEmpty();
    }

    @Test
    @DisplayName("분석 결과가 그대로 RunningRecord가 된다")
    void analysisBuildsValidRunningRecord() {
        // given
        TrackAnalysis analysis = analyze(track(1_800, 2.8, null));

        // when & then -> 파이프라인 전체가 도메인 불변식을 만족하는지 보는 관문이다
        assertThatCode(() -> RunningRecord.finish()
                .runningRoomId(125L)
                .userId(USER_ID)
                .avgPace(analysis.avgPaceSecondsPerKm())
                .totalDistance(analysis.totalDistanceMeters())
                .totalDuration(analysis.totalDurationSeconds())
                .totalCalories(368)
                .gpsTrackKey("gps-tracks/u/125/2026-08-27.json")
                .segments(analysis.segments())
                .startAt(analysis.startAt())
                .endAt(analysis.endAt())
                .weatherCode(0)
                .temperature(new BigDecimal("15.0"))
                .totalElevationGain(analysis.totalElevationGainMeters())
                .splits(analysis.splits())
                .build()).doesNotThrowAnyException();
    }

    // 인코딩된 폴리라인에서 좌표 개수를 센다 — continuation 비트가 꺼진 문자가 값 하나의 끝이다
    private static int countPolylinePoints(String encoded) {
        int values = 0;
        for (int i = 0; i < encoded.length(); i++) {
            if ((encoded.charAt(i) - 63) < 0x20) {
                values++;
            }
        }
        return values / 2;   // 위도·경도 한 쌍이 좌표 하나다
    }

    @Test
    @DisplayName("결과가 없을 때는 Optional로 알린다")
    void returnsOptionalInsteadOfThrowing() {
        // when
        Optional<TrackAnalysis> analysis = TrackAnalyzer.analyze(
                unfiltered(track(2, 2.8, null)), TARGET, WEIGHT, PROPERTIES);

        // then -> 예외로 흐름을 만들지 않는다
        assertThat(analysis).isEmpty();
    }

    @Test
    @DisplayName("멈춘 시간은 총 시간에서 빠지고 기록 기간에는 남는다")
    void stopIsExcludedFromDurationButNotFromPeriod() {
        // given -> 2.8m/s로 200초, 제자리에서 60초 흔들림, 다시 2.8m/s로 200초
        List<TrackPoint> points = new ArrayList<>();
        long sequence = 0;
        for (int i = 0; i < 200; i++) {
            points.add(point(sequence++, 37.5 + i * 2.8 / METERS_PER_DEGREE, null,
                    START.plusSeconds(i)));
        }
        double stopNorth = 199 * 2.8;
        for (int i = 1; i <= 30; i++) {
            double jitter = i % 2 == 0 ? 0.5 : -0.5;
            points.add(point(sequence++, 37.5 + (stopNorth + jitter) / METERS_PER_DEGREE, null,
                    START.plusSeconds(199 + i * 2L)));
        }
        for (int i = 1; i <= 200; i++) {
            points.add(point(sequence++, 37.5 + (stopNorth + i * 2.8) / METERS_PER_DEGREE, null,
                    START.plusSeconds(259 + i)));
        }

        // when -> 운영 판정값으로 실제 필터를 거친다
        TrackAnalysis analysis = TrackAnalyzer.analyze(
                TrackFilter.apply(points, DEFAULT_PROPERTIES), TARGET, WEIGHT, PROPERTIES)
                .orElseThrow();

        // then -> 움직인 시간은 약 400초, 실제 기간은 멈춘 60초를 더해 약 460초다
        long periodSeconds = Duration.between(analysis.startAt(), analysis.endAt()).toSeconds();
        assertThat(analysis.totalDurationSeconds()).isBetween(390, 405);
        assertThat(periodSeconds).isGreaterThanOrEqualTo(450);
        assertThat(analysis.avgPaceSecondsPerKm()).isBetween(350, 365);
    }

    @Test
    @DisplayName("끊긴 곳이 없으면 경로 조각 하나에 모든 구간이 속한다")
    void noBreakMakesSingleSegment() {
        // when
        TrackAnalysis analysis = analyze(track(1_800, 2.8, null));

        // then
        assertThat(analysis.segments()).extracting(SegmentDraft::segmentNumber).containsExactly(1);
        assertThat(analysis.splits()).allSatisfy(split -> assertThat(split.segmentNumber()).isEqualTo(1));
    }

    @Test
    @DisplayName("거부한 구간에서 경로가 두 조각으로 나뉘고 구간은 끝점이 있는 조각에 속한다")
    void rejectedGapSplitsRouteIntoSegments() {
        // given -> 2.8m/s로 600초, 60초 만에 400m 앞(택시), 다시 2.8m/s로 600초
        List<TrackPoint> points = new ArrayList<>();
        for (int i = 0; i <= 600; i++) {
            points.add(point(points.size(), 37.5 + i * 2.8 / METERS_PER_DEGREE, null,
                    START.plusSeconds(i)));
        }
        double resumeNorth = 600 * 2.8 + 400.0;
        for (int i = 0; i <= 600; i++) {
            points.add(point(points.size(), 37.5 + (resumeNorth + i * 2.8) / METERS_PER_DEGREE, null,
                    START.plusSeconds(660 + i)));
        }

        // when -> 운영 판정값으로 실제 필터를 거친다
        TrackAnalysis analysis = TrackAnalyzer.analyze(
                TrackFilter.apply(points, DEFAULT_PROPERTIES), TARGET, WEIGHT, PROPERTIES)
                .orElseThrow();

        // then -> 조각은 점을 나눌 뿐이라 전 조각의 점 수 합이 구간 수 + 1이다
        List<SegmentDraft> segments = analysis.segments();
        assertThat(segments).extracting(SegmentDraft::segmentNumber).containsExactly(1, 2);
        int firstSegmentPoints = PolylineDecoder.decode(segments.get(0).routePolyline()).size();
        int totalPoints = segments.stream()
                .mapToInt(segment -> PolylineDecoder.decode(segment.routePolyline()).size())
                .sum();
        assertThat(totalPoints).isEqualTo(analysis.splits().size() + 1);
        // 택시를 탄 칸에 걸친 구간은 2번 조각의 첫 점 하나만 가리킨다
        SplitDraft across = analysis.splits().get(firstSegmentPoints - 1);
        assertThat(across.segmentNumber()).isEqualTo(2);
        assertThat(across.routeStartIndex()).isZero();
        assertThat(across.routeEndIndex()).isZero();
        assertThat(analysis.splits().get(firstSegmentPoints - 2).segmentNumber()).isEqualTo(1);
    }

    @Test
    @DisplayName("두 조각으로 나뉜 분석 결과로 기록을 만들 수 있다")
    void segmentedAnalysisSatisfiesRecordInvariants() {
        // given -> 2.8m/s로 600초, 60초 만에 400m 앞(택시), 다시 2.8m/s로 600초
        List<TrackPoint> points = new ArrayList<>();
        for (int i = 0; i <= 600; i++) {
            points.add(point(points.size(), 37.5 + i * 2.8 / METERS_PER_DEGREE, null,
                    START.plusSeconds(i)));
        }
        double resumeNorth = 600 * 2.8 + 400.0;
        for (int i = 0; i <= 600; i++) {
            points.add(point(points.size(), 37.5 + (resumeNorth + i * 2.8) / METERS_PER_DEGREE, null,
                    START.plusSeconds(660 + i)));
        }
        TrackAnalysis analysis = TrackAnalyzer.analyze(
                TrackFilter.apply(points, DEFAULT_PROPERTIES), TARGET, WEIGHT, PROPERTIES)
                .orElseThrow();

        // when & then -> 필터 → 조각 → 구간 → 기록 검증이 한 줄로 이어지는지 보는 관문이다
        assertThat(analysis.segments()).hasSize(2);
        assertThatCode(() -> RunningRecord.finish()
                .runningRoomId(125L)
                .userId(USER_ID)
                .avgPace(analysis.avgPaceSecondsPerKm())
                .totalDistance(analysis.totalDistanceMeters())
                .totalDuration(analysis.totalDurationSeconds())
                .totalCalories(368)
                .gpsTrackKey("gps-tracks/u/125/2026-08-27.json")
                .segments(analysis.segments())
                .startAt(analysis.startAt())
                .endAt(analysis.endAt())
                .weatherCode(0)
                .temperature(new BigDecimal("15.0"))
                .splits(analysis.splits())
                .build()).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("일시정지에서 경로가 두 조각으로 나뉘고 걸친 구간은 뒤 조각의 첫 점 하나다")
    void pauseSplitsRouteIntoSegments() {
        // given -> 2.8m/s로 600초, 일시정지한 채 60초 동안 168m(자기 페이스) 옮겨 재개, 다시 2.8m/s로 600초
        List<TrackPoint> points = new ArrayList<>();
        for (int i = 0; i <= 600; i++) {
            points.add(new TrackPoint(points.size(), 37.5 + i * 2.8 / METERS_PER_DEGREE, 127.0,
                    null, 5.0, null, null, 168, null, START.plusSeconds(i), 0));
        }
        double resumeNorth = 600 * 2.8 + 168.0;
        for (int i = 0; i <= 600; i++) {
            points.add(new TrackPoint(points.size(), 37.5 + (resumeNorth + i * 2.8) / METERS_PER_DEGREE, 127.0,
                    null, 5.0, null, null, 168, null, START.plusSeconds(660 + i), 1));
        }

        // when -> 운영 판정값으로 실제 필터를 거친다
        TrackAnalysis analysis = TrackAnalyzer.analyze(
                TrackFilter.apply(points, DEFAULT_PROPERTIES), TARGET, WEIGHT, PROPERTIES)
                .orElseThrow();

        // then -> 옮긴 168m는 거리·시간·경로 어디에도 들어가지 않는다
        assertThat(analysis.segments()).extracting(SegmentDraft::segmentNumber).containsExactly(1, 2);
        assertThat(analysis.totalDistanceMeters()).isBetween(3_350, 3_360);
        assertThat(analysis.totalDurationSeconds()).isBetween(1_195, 1_205);
        int firstSegmentPoints = PolylineDecoder.decode(analysis.segments().get(0).routePolyline()).size();
        SplitDraft across = analysis.splits().get(firstSegmentPoints - 1);
        assertThat(across.segmentNumber()).isEqualTo(2);
        assertThat(across.routeStartIndex()).isZero();
        assertThat(across.routeEndIndex()).isZero();
        // 기록 검증까지 한 줄로 이어지는지 본다
        assertThatCode(() -> RunningRecord.finish()
                .runningRoomId(125L)
                .userId(USER_ID)
                .avgPace(analysis.avgPaceSecondsPerKm())
                .totalDistance(analysis.totalDistanceMeters())
                .totalDuration(analysis.totalDurationSeconds())
                .totalCalories(250)
                .gpsTrackKey("gps-tracks/u/125/2026-08-27.json")
                .segments(analysis.segments())
                .startAt(analysis.startAt())
                .endAt(analysis.endAt())
                .weatherCode(0)
                .temperature(new BigDecimal("15.0"))
                .splits(analysis.splits())
                .build()).doesNotThrowAnyException();
    }

    // 북쪽으로 초당 2.8m 달리는 좌표 — 고도와 일시정지 구간 번호를 정한다. 위치는 끊김 없이 이어진다
    private static TrackPoint climbing(int i, double altitude, int pauseSegment) {
        return new TrackPoint(i, 37.5 + i * 2.8 / METERS_PER_DEGREE, 127.0, altitude, 5.0,
                null, null, 168, null, START.plusSeconds(i), pauseSegment);
    }

    @Test
    @DisplayName("일시정지 중 오른 고도는 누적 상승에 넣지 않는다")
    void elevationGainSkipsClimbDuringPause() {
        // given -> 고도 10m 평지를 200초 달리고 일시정지, 30m 위(40m)에서 재개해 200초 평지를 달린다
        List<TrackPoint> points = new ArrayList<>();
        for (int i = 0; i < 200; i++) {
            points.add(climbing(i, 10.0, 0));
        }
        for (int i = 200; i < 400; i++) {
            points.add(climbing(i, 40.0, 1));
        }

        // when
        TrackAnalysis analysis = analyze(points);

        // then -> 경계에서 기준을 새로 잡지 않으면 30m가 상승으로 들어간다
        assertThat(analysis.totalElevationGainMeters()).isZero();
    }

    @Test
    @DisplayName("재개 뒤 오르막은 새 기준 고도에서 센다")
    void elevationGainCountsClimbAfterResume() {
        // given -> 10m 평지 200초, 일시정지 후 40m에서 재개해 200초 동안 60m까지 오른다
        List<TrackPoint> points = new ArrayList<>();
        for (int i = 0; i < 200; i++) {
            points.add(climbing(i, 10.0, 0));
        }
        for (int i = 200; i < 400; i++) {
            points.add(climbing(i, 40.0 + (i - 200) * 0.1, 1));
        }

        // when
        TrackAnalysis analysis = analyze(points);

        // then -> 일시정지 중 30m는 빠지고 뛰어서 오른 약 20m만 남는다(노이즈 임계값 단위로 끊겨 센다)
        assertThat(analysis.totalElevationGainMeters()).isBetween(17, 20);
    }
}
