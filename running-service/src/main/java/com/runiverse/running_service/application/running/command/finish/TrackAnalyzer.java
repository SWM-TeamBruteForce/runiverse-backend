package com.runiverse.running_service.application.running.command.finish;

import com.runiverse.running_service.application.running.port.out.TrackPoint;
import com.runiverse.running_service.domain.running.metric.vo.ElevationGain;
import com.runiverse.running_service.domain.running.metric.vo.Pace;
import com.runiverse.running_service.domain.running.record.SplitDraft;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

// 실측 트랙 하나를 기록에 넣을 값들로 바꾼다.
// 결과가 비면 "거리·시간·경로를 산출할 수 없는 트랙"이라 기록 없이 상태만 확정한다(api-spec 5-D).
public final class TrackAnalyzer {

    private static final int METERS_PER_KM = 1_000;

    private TrackAnalyzer() {
    }

    public static Optional<TrackAnalysis> analyze(List<TrackPoint> points, int targetDistanceMeters,
                                                  BigDecimal weightKg,
                                                  RunningFinishProperties properties) {
        int interval = properties.splitDistanceMeters();
        List<BoundaryPoint> boundaries = TrackResampler.resample(
                points, TrackDistance.cumulativeMeters(points), targetDistanceMeters, interval);
        if (boundaries.size() < 2) {
            return Optional.empty();
        }
        List<SplitDraft> splits = SplitAssembler.assemble(boundaries, points, interval, weightKg,
                properties.elevationNoiseThresholdMeters());

        int totalDistance = boundaries.get(boundaries.size() - 1).distanceMeters();
        int totalDuration = splits.stream().mapToInt(SplitDraft::duration).sum();
        int avgPace = totalDuration * METERS_PER_KM / totalDistance;

        // 기록으로 남길 만한 러닝인지. 페이스까지 미리 보는 이유는,
        // 여기서 안 거르면 RunningRecord 생성에서 예외가 나 기록이 통째로 사라지기 때문이다
        if (totalDistance < properties.minDistanceMeters()
                || totalDuration < properties.minDurationSeconds()
                || !Pace.isValid(avgPace)) {
            return Optional.empty();
        }

        return Optional.of(new TrackAnalysis(
                totalDistance,
                totalDuration,
                avgPace,
                elevationGain(points, properties.elevationNoiseThresholdMeters()),
                PolylineEncoder.encode(boundaries),
                splits.get(0).startAt(),
                splits.get(splits.size() - 1).endAt(),
                splits));
    }

    // 누적 상승은 실측점으로 낸다 — 경계점은 직선 보간이라 오르내림이 뭉개진다.
    // 구간의 순고도차(끝 − 시작)와 달리 올라간 것만 더하므로 구간 합과 일치하지 않는다(erd.md)
    private static Integer elevationGain(List<TrackPoint> points, double noiseThresholdMeters) {
        Double previous = null;
        double gain = 0;
        int samples = 0;
        for (TrackPoint point : points) {
            Double altitude = point.altitudeMeters();
            if (altitude == null || !Double.isFinite(altitude)) {
                continue;
            }
            samples++;
            if (previous != null && altitude - previous > noiseThresholdMeters) {
                gain += altitude - previous;
            }
            previous = altitude;
        }
        if (samples < 2) {
            return null;
        }
        // 캐스트로 자르기 전에 long으로 검사한다 — 글리치가 만든 초과 상승은 측정 실패와 같다
        long rounded = Math.round(gain);
        return ElevationGain.isValid(rounded) ? (int) rounded : null;
    }
}
