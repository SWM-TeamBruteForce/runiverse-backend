package com.runiverse.running_service.application.running.common;

import com.runiverse.running_service.application.running.port.out.TrackPoint;
import com.runiverse.running_service.domain.running.metric.vo.Cadence;
import com.runiverse.running_service.domain.running.metric.vo.ElapsedTime;
import com.runiverse.running_service.domain.running.metric.vo.ElevationChange;
import com.runiverse.running_service.domain.running.record.SplitDraft;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.function.ToDoubleFunction;

// 경계점과 실측점으로 구간 기록을 조립한다.
// 위치·시각은 경계점이, 센서값(케이던스·고도)은 그 구간에 속한 실측점이 낸다.
public final class SplitAssembler {

    private static final double MILLIS_PER_SECOND = 1_000.0;
    private static final int METERS_PER_KM = 1_000;

    private SplitAssembler() {
    }

    // 구간 N은 경계점 N-1 → N이다. 끝 경계점이 있는 조각에 속하고, 경로 범위는 그 조각 안의 번호다.
    // 시작 경계점이 앞 조각에 있으면(끊긴 곳에 걸친 구간) 이 조각의 첫 점 하나만 가리킨다 — [0, 0]
    public static List<SplitDraft> assemble(List<List<BoundaryPoint>> segments,
                                            List<TrackPoint> points, int intervalMeters,
                                            BigDecimal weightKg,
                                            double elevationNoiseThresholdMeters) {
        // 시각·시간·센서값은 조각과 무관하게 이어진 경계점 위에서 낸다
        List<BoundaryPoint> boundaries = segments.stream().flatMap(List::stream).toList();
        if (boundaries.size() < 2) {
            return List.of();
        }
        // 경계점마다 몇 번 조각의 몇 번째 점인지 — 조각 번호는 1부터, 조각 안의 번호는 0부터다
        int[] segmentOf = new int[boundaries.size()];
        int[] indexInSegment = new int[boundaries.size()];
        int position = 0;
        for (int segment = 0; segment < segments.size(); segment++) {
            for (int index = 0; index < segments.get(segment).size(); index++, position++) {
                segmentOf[position] = segment + 1;
                indexInSegment[position] = index;
            }
        }
        LocalDateTime origin = boundaries.get(0).recordedAt();
        // 시각은 실제 시각, 시간은 움직인 시간이다 — 멈춘 시간을 빼야 구간 페이스가 실제로 뛴 속도가 되고,
        // 시각까지 움직인 시간으로 세면 쉰 만큼 뒤 구간이 전부 앞당겨진 시각으로 저장된다
        long[] real = monotonicSeconds(boundaries,
                boundary -> Duration.between(origin, boundary.recordedAt()).toMillis()
                        / MILLIS_PER_SECOND);
        long[] moving = monotonicSeconds(boundaries, BoundaryPoint::movingSeconds);
        // 시계가 통째로 밀린 트랙은 구간을 만들지 않는다 — 단조화된 마지막 경과가 곧 조립에 쓰는 값이라
        // 중간부터 밀린 트랙도 여기서 걸린다. 빈 결과는 "산출 불가 → 기록 없이 상태만 확정"의 기존 경로다
        if (!ElapsedTime.isValid(real[real.length - 1])
                || !ElapsedTime.isValid(moving[moving.length - 1])) {
            return List.of();
        }

        List<SplitDraft> drafts = new ArrayList<>(boundaries.size() - 1);
        // 칼로리도 시간처럼 누적값을 반올림한 차이로 낸다 — 10m 구간 하나는 1kcal에 못 미쳐서,
        // 구간마다 반올림하면 전부 1이 돼 1km 합이 실제보다 크게 부푼다
        double cumulativeKcal = 0;
        long previousRoundedKcal = 0;
        for (int number = 1; number < boundaries.size(); number++) {
            BoundaryPoint from = boundaries.get(number - 1);
            BoundaryPoint to = boundaries.get(number);
            int duration = (int) (moving[number] - moving[number - 1]);
            int avgPace = Math.max(1, duration * METERS_PER_KM / intervalMeters);
            cumulativeKcal += CalorieCalculator.unroundedKcal(avgPace, duration, weightKg);
            long roundedKcal = Math.round(cumulativeKcal);
            int routeEnd = indexInSegment[number];
            int routeStart = segmentOf[number - 1] == segmentOf[number]
                    ? indexInSegment[number - 1]
                    : routeEnd;

            drafts.add(SplitDraft.create(
                    number,
                    segmentOf[number],
                    avgPace,
                    intervalMeters,
                    duration,
                    routeStart,
                    routeEnd,
                    origin.plusSeconds(real[number - 1]),
                    origin.plusSeconds(real[number]),
                    (int) (roundedKcal - previousRoundedKcal),
                    averageCadence(points, from.sourceIndex(), to.sourceIndex()),
                    elevationChange(points, from.sourceIndex(), to.sourceIndex(),
                            elevationNoiseThresholdMeters)));
            previousRoundedKcal = roundedKcal;
        }
        return drafts;
    }


    // 시작점부터의 경과 초를 미리 확정한다. 구간마다 따로 반올림하면 500개에서 오차가 쌓여
    // 구간 시간의 합이 총 시간과 어긋난다 — total_duration은 구간 duration의 합이다.
    // 최소 1초씩 벌리는 이유는 좌표 시각이 초 단위라 경계 둘이 같은 초에 걸릴 수 있어서다 —
    // 그대로 두면 ElapsedTime(MIN 1)과 RunningPeriod(끝 > 시작)가 둘 다 터진다.
    // 실제 시각과 움직인 시간에 같은 규칙을 쓰므로 기준값만 받는다
    private static long[] monotonicSeconds(List<BoundaryPoint> boundaries,
                                           ToDoubleFunction<BoundaryPoint> secondsOf) {
        double start = secondsOf.applyAsDouble(boundaries.get(0));
        long[] elapsed = new long[boundaries.size()];
        for (int i = 1; i < boundaries.size(); i++) {
            long rounded = Math.round(secondsOf.applyAsDouble(boundaries.get(i)) - start);
            elapsed[i] = Math.max(rounded, elapsed[i - 1] + 1);
        }
        return elapsed;
    }

    // 보간점에는 센서값이 없다 — 경계 사이의 실측점만 본다
    private static Integer averageCadence(List<TrackPoint> points, int fromExclusive,
                                          int toInclusive) {
        int sum = 0;
        int count = 0;
        for (int i = fromExclusive + 1; i <= toInclusive; i++) {
            Integer cadence = points.get(i).cadenceSpm();
            // 범위 밖 표본은 오전송(누적 걸음수 등)이다 — 쓰레기 하나가 평균을 오염시키지 않게 버린다.
            // 남은 표본이 전부 범위 안이면 평균도 범위 안이라 결과 재검은 필요 없다
            if (cadence != null && Cadence.isValid(cadence)) {
                sum += cadence;
                count++;
            }
        }
        return count == 0 ? null : sum / count;
    }

    // 일시정지를 걸친 구간은 경계 앞과 뒤의 순고도차를 따로 내서 더한다 —
    // 구간 끝 − 시작으로 내면 일시정지 중 오르내린 고도가 들어간다
    private static Integer elevationChange(List<TrackPoint> points, int fromExclusive,
                                           int toInclusive, double noiseThresholdMeters) {
        double change = 0;
        boolean measured = false;
        Double first = null;
        Double last = null;
        int segment = 0;
        int count = 0;
        for (int i = fromExclusive + 1; i <= toInclusive; i++) {
            TrackPoint point = points.get(i);
            Double altitude = point.altitudeMeters();
            if (altitude == null || !Double.isFinite(altitude)) {
                continue;
            }
            // 경계를 넘으면 앞쪽 조각의 순고도차를 닫고 새로 잰다
            if (first != null && point.pauseSegment() != segment) {
                if (count >= 2) {
                    change += last - first;
                    measured = true;
                }
                first = null;
                count = 0;
            }
            if (first == null) {
                first = altitude;
                segment = point.pauseSegment();
            }
            last = altitude;
            count++;
        }
        if (count >= 2) {
            change += last - first;
            measured = true;
        }
        if (!measured) {
            return null;   // 어느 쪽에도 표본이 둘 이상 없으면 변화를 말할 수 없다
        }
        // 임계값 이하는 GPS 수직 오차로 본다 — 10m 구간에서는 대체로 여기 걸려 null이 된다
        if (Math.abs(change) <= noiseThresholdMeters) {
            return null;
        }
        // 캐스트로 자르기 전에 long으로 검사한다 — 글리치 고도는 측정 실패와 같다
        long rounded = Math.round(change);
        return ElevationChange.isValid(rounded) ? (int) rounded : null;
    }
}
