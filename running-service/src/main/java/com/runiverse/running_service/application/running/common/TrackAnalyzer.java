package com.runiverse.running_service.application.running.common;

import com.runiverse.running_service.application.running.port.out.TrackPoint;
import com.runiverse.running_service.domain.running.metric.vo.ElevationGain;
import com.runiverse.running_service.domain.running.metric.vo.Pace;
import com.runiverse.running_service.domain.running.record.SegmentDraft;
import com.runiverse.running_service.domain.running.record.SplitDraft;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

// 실측 트랙 하나를 기록에 넣을 값들로 바꾼다.
// 결과가 비면 "거리·시간·경로를 산출할 수 없는 트랙"이라 기록 없이 상태만 확정한다.
public final class TrackAnalyzer {

    private static final int METERS_PER_KM = 1_000;

    private TrackAnalyzer() {
    }

    // 실측 트랙 하나를 기록에 넣을 값들로 바꾼다 — 오염 좌표는 이미 걸러져 들어온다
    public static Optional<TrackAnalysis> analyze(FilteredTrack track, int targetDistanceMeters,
                                                  BigDecimal weightKg,
                                                  RunningFinishProperties properties) {
        int interval = properties.splitDistanceMeters();
        List<TrackPoint> points = track.points();
        double[] cumulative = track.cumulativeMeters();
        List<BoundaryPoint> boundaries = TrackResampler.resample(
                track, targetDistanceMeters, interval);
        if (boundaries.size() < 2) {
            return Optional.empty();
        }
        // 끊긴 곳에서 조각으로 나눈다 — 구간도 이 조각 기준으로 경로 범위를 잡는다
        List<List<BoundaryPoint>> segments = RouteSegmenter.split(boundaries, track.breakEdges());
        List<SplitDraft> splits = SplitAssembler.assemble(segments, points, interval, weightKg,
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
                elevationGain(recordedPoints(points, cumulative, totalDistance),
                        properties.elevationNoiseThresholdMeters()),
                IntStream.range(0, segments.size())
                        .mapToObj(i -> new SegmentDraft(i + 1, PolylineEncoder.encode(segments.get(i))))
                        .toList(),
                splits.get(0).startAt(),
                splits.get(splits.size() - 1).endAt(),
                splits));

    }

    // 누적 상승도 거리·시간·경로와 같은 절단을 따른다 — 확정 총거리 밖의 실측점은 기록 밖이다.
    // 경계에 정확히 걸친 실측점까지 포함해야 해서 sourceIndex가 아니라 누적 거리로 자른다
    private static List<TrackPoint> recordedPoints(List<TrackPoint> points, double[] cumulative,
                                                   int totalDistanceMeters) {
        int end = points.size();
        while (end > 0 && cumulative[end - 1] > totalDistanceMeters) {
            end--;
        }
        return points.subList(0, end);
    }

    // 누적 상승은 실측점으로 낸다 — 경계점은 직선 보간이라 오르내림이 뭉개진다.
    // 구간의 순고도차(끝 − 시작)와 달리 올라간 것만 더하므로 구간 합과 일치하지 않는다
    private static Integer elevationGain(List<TrackPoint> points, double noiseThresholdMeters) {
        Double anchor = null;
        int anchorSegment = 0;
        double gain = 0;
        int samples = 0;
        for (TrackPoint point : points) {
            Double altitude = point.altitudeMeters();
            if (altitude == null || !Double.isFinite(altitude)) {
                continue;
            }
            samples++;
            // 기준 고도는 임계값을 넘게 움직였을 때만 옮긴다 — 바로 앞 좌표와 비교하면
            // 1초 간격의 완만한 오르막은 매번 임계값 아래라 전부 노이즈로 걸린다.
            // 일시정지 경계를 넘으면 기준을 새로 잡는다 — 일시정지 중 오르내린 고도는 넣지 않는다
            if (anchor == null || point.pauseSegment() != anchorSegment) {
                anchor = altitude;
                anchorSegment = point.pauseSegment();
            } else if (Math.abs(altitude - anchor) > noiseThresholdMeters) {
                gain += Math.max(0, altitude - anchor);
                anchor = altitude;
            }
        }
        if (samples < 2) {
            return null;
        }
        // 캐스트로 자르기 전에 long으로 검사한다 — 글리치가 만든 초과 상승은 측정 실패와 같다
        long rounded = Math.round(gain);
        return ElevationGain.isValid(rounded) ? (int) rounded : null;
    }
}
