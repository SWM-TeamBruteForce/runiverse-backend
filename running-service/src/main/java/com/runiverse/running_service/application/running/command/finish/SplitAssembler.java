package com.runiverse.running_service.application.running.command.finish;

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

// 경계점과 실측점으로 구간 기록을 조립한다.
// 위치·시각은 경계점이, 센서값(케이던스·고도)은 그 구간에 속한 실측점이 낸다.
public final class SplitAssembler {

    private static final double MILLIS_PER_SECOND = 1_000.0;
    private static final int METERS_PER_KM = 1_000;

    private SplitAssembler() {
    }

    public static List<SplitDraft> assemble(List<BoundaryPoint> boundaries, List<TrackPoint> points,
                                            int intervalMeters, BigDecimal weightKg,
                                            double elevationNoiseThresholdMeters) {
        if (boundaries.size() < 2) {
            return List.of();
        }
        LocalDateTime origin = boundaries.get(0).recordedAt();
        long[] elapsed = elapsedSeconds(boundaries, origin);
        // 시계가 튄 트랙은 구간을 만들지 않는다 — 단조화된 마지막 경과가 곧 조립에 쓰는 값이라
        // 중간만 튀었다 돌아온 트랙도 여기서 걸린다. 빈 결과는 "산출 불가 → 기록 없이 상태만 확정"의 기존 경로다
        if (!ElapsedTime.isValid(elapsed[elapsed.length - 1])) {
            return List.of();
        }

        List<SplitDraft> drafts = new ArrayList<>(boundaries.size() - 1);
        for (int number = 1; number < boundaries.size(); number++) {
            BoundaryPoint from = boundaries.get(number - 1);
            BoundaryPoint to = boundaries.get(number);
            int duration = (int) (elapsed[number] - elapsed[number - 1]);
            int avgPace = Math.max(1, duration * METERS_PER_KM / intervalMeters);

            drafts.add(SplitDraft.create(
                    number,
                    avgPace,
                    intervalMeters,
                    duration,
                    number - 1,     // 폴리라인이 곧 경계점 목록이라 인덱스가 구간 번호를 그대로 따른다
                    number,
                    origin.plusSeconds(elapsed[number - 1]),
                    origin.plusSeconds(elapsed[number]),
                    CalorieCalculator.kcal(avgPace, duration, weightKg),
                    averageCadence(points, from.sourceIndex(), to.sourceIndex()),
                    elevationChange(points, from.sourceIndex(), to.sourceIndex(),
                            elevationNoiseThresholdMeters)));
        }
        return drafts;
    }

    // 시작점부터의 경과 초를 미리 확정한다. 구간마다 따로 반올림하면 500개에서 오차가 쌓여
    // 구간 시간의 합이 총 시간과 어긋난다 — total_duration은 구간 duration의 합이다.
    // 최소 1초씩 벌리는 이유는 좌표 시각이 초 단위라 경계 둘이 같은 초에 걸릴 수 있어서다 —
    // 그대로 두면 ElapsedTime(MIN 1)과 RunningPeriod(끝 > 시작)가 둘 다 터진다.
    private static long[] elapsedSeconds(List<BoundaryPoint> boundaries, LocalDateTime origin) {
        long[] elapsed = new long[boundaries.size()];
        for (int i = 1; i < boundaries.size(); i++) {
            long rounded = Math.round(
                    Duration.between(origin, boundaries.get(i).recordedAt()).toMillis()
                            / MILLIS_PER_SECOND);
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

    private static Integer elevationChange(List<TrackPoint> points, int fromExclusive,
                                           int toInclusive, double noiseThresholdMeters) {
        Double first = null;
        Double last = null;
        int count = 0;
        for (int i = fromExclusive + 1; i <= toInclusive; i++) {
            Double altitude = points.get(i).altitudeMeters();
            if (altitude == null || !Double.isFinite(altitude)) {
                continue;
            }
            if (first == null) {
                first = altitude;
            }
            last = altitude;
            count++;
        }
        if (count < 2) {
            return null;   // 표본이 없거나 하나뿐이면 변화를 말할 수 없다
        }
        double change = last - first;
        // 임계값 이하는 GPS 수직 오차로 본다 — 10m 구간에서는 대체로 여기 걸려 null이 된다
        if (Math.abs(change) <= noiseThresholdMeters) {
            return null;
        }
        // 캐스트로 자르기 전에 long으로 검사한다 — 글리치 고도는 측정 실패와 같다
        long rounded = Math.round(change);
        return ElevationChange.isValid(rounded) ? (int) rounded : null;
    }
}
