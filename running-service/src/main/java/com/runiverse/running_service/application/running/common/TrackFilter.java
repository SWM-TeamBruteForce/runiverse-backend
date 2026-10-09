package com.runiverse.running_service.application.running.common;

import com.runiverse.running_service.application.running.port.out.TrackPoint;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalDouble;

// 기록 계산 전에 오염된 좌표를 거른다 — 멈춘 동안의 흔들림, GPS 튐, 관측하지 못한 이동.
// 칸(이웃한 두 좌표)마다 인정 거리·인정 시간을 정하고, 거부한 칸은 둘을 함께 0으로 한다.
// 하나만 빼면 시간 없이 거리만 늘거나 그 반대가 돼 구간 페이스가 틀어진다
public final class TrackFilter {

    // 좌표 시각이 초 단위라 같은 초에 찍힌 두 점은 시간 차가 0이다 — 속도가 무한대로 튀지 않게 한다
    private static final double MIN_EDGE_SECONDS = 1.0;
    private static final double MILLIS_PER_SECOND = 1_000.0;

    private TrackFilter() {
    }

    public static FilteredTrack apply(List<TrackPoint> rawPoints, TrackFilterProperties properties) {
        Stats stats = new Stats();
        stats.rawMeters = pathMeters(rawPoints);
        List<TrackPoint> accurate = dropInaccurate(rawPoints, properties, stats);
        List<TrackPoint> points = dropSpikes(accurate, properties, stats);
        // 정지를 먼저 판정한다 — 관측 안 된 칸의 직전 속도에서 정지 구간을 빼야 해서다.
        // 반경을 벗어나는 칸은 인정 여부와 무관하게 기준점을 새로 잡으므로 순서를 바꿔도 정지 결과는 같다
        boolean[] stopEdges = detectStops(points, properties, stats);

        int size = points.size();
        double[] cumulativeMeters = new double[size];
        double[] cumulativeSeconds = new double[size];
        // 평소 칸으로 인정한 칸의 거리·시간 — 관측 안 된 칸의 직전 속도를 여기서 낸다
        double[] runMeters = new double[size];
        double[] runSeconds = new double[size];
        for (int edge = 1; edge < size; edge++) {
            TrackPoint from = points.get(edge - 1);
            TrackPoint to = points.get(edge);
            double meters = TrackDistance.between(from, to);
            double seconds = seconds(from, to);
            boolean accepted;
            if (stopEdges[edge]) {
                accepted = false;
            } else if (to.recordedAt().isBefore(from.recordedAt())) {
                // 시계가 뒤로 간 칸이다(재부팅·시간대 변경) — 시간을 믿을 수 없어 거리도 함께 뺀다
                accepted = false;
            } else if (seconds > properties.gapMinSeconds()) {
                accepted = acceptGap(points, runMeters, runSeconds, edge, meters, seconds,
                        properties, stats);
            } else if (meters / Math.max(seconds, MIN_EDGE_SECONDS)
                    > properties.maxSpeedMetersPerSecond()) {
                // 튄 자리에서 이어 간 경우다 — 점이 아니라 이 칸 하나가 문제라 칸만 0으로 한다
                stats.jumpEdgeCount++;
                stats.spikeRemovedMeters += meters;
                accepted = false;
            } else {
                runMeters[edge] = meters;
                runSeconds[edge] = seconds;
                accepted = true;
            }
            cumulativeMeters[edge] = cumulativeMeters[edge - 1] + (accepted ? meters : 0);
            cumulativeSeconds[edge] = cumulativeSeconds[edge - 1] + (accepted ? seconds : 0);
        }
        stats.filteredMeters = size == 0 ? 0 : cumulativeMeters[size - 1];
        return new FilteredTrack(points, cumulativeMeters, cumulativeSeconds, stats.toSummary());
    }

    private static List<TrackPoint> dropInaccurate(List<TrackPoint> points,
                                                   TrackFilterProperties properties, Stats stats) {
        List<TrackPoint> kept = points.stream()
                .filter(point -> point.accuracyMeters() <= properties.maxAccuracyMeters())
                .toList();
        stats.accuracyDroppedCount = points.size() - kept.size();
        // 점을 빼면 경로는 짧아지기만 한다 — 차이가 곧 이 단계가 뺀 거리다
        stats.accuracyRemovedMeters = stats.rawMeters - pathMeters(kept);
        return kept;
    }

    // 튀었다 돌아온 점만 뺀다 — 앞뒤 칸이 모두 사람이 낼 수 없는 속도인데 그 점을 건너뛰면 정상인 경우다
    private static List<TrackPoint> dropSpikes(List<TrackPoint> points,
                                               TrackFilterProperties properties, Stats stats) {
        List<TrackPoint> kept = new ArrayList<>(points.size());
        for (int i = 0; i < points.size(); i++) {
            TrackPoint point = points.get(i);
            if (!kept.isEmpty() && i + 1 < points.size()) {
                TrackPoint previous = kept.getLast();
                TrackPoint next = points.get(i + 1);
                if (tooFast(previous, point, properties) && tooFast(point, next, properties)
                        && !tooFast(previous, next, properties)) {
                    stats.spikePointCount++;
                    stats.spikeRemovedMeters += TrackDistance.between(previous, point)
                            + TrackDistance.between(point, next)
                            - TrackDistance.between(previous, next);
                    continue;
                }
            }
            kept.add(point);
        }
        return kept;
    }

    // 기준점 반경 안에 최소 시간 이상 머문 구간을 정지로 본다.
    // 머문 시간은 반경 안에서 관측한 좌표의 시각으로만 잰다 — 반경 밖 좌표가 도착하기까지로 재면
    // 비었다가 멀리서 나타난 칸(터널)이 정지로 잡힌다
    private static boolean[] detectStops(List<TrackPoint> points,
                                         TrackFilterProperties properties, Stats stats) {
        boolean[] stopEdges = new boolean[points.size()];
        if (points.isEmpty()) {
            return stopEdges;
        }
        int anchor = 0;
        int inside = 0;
        for (int i = 1; i < points.size(); i++) {
            double fromAnchor = TrackDistance.between(points.get(anchor), points.get(i));
            if (fromAnchor <= radius(points.get(i), properties, stats)) {
                inside = i;
                continue;
            }
            markStop(points, anchor, inside, stopEdges, properties, stats);
            anchor = i;
            inside = i;
        }
        markStop(points, anchor, inside, stopEdges, properties, stats);   // 멈춘 채 끝난 트랙
        return stopEdges;
    }

    private static void markStop(List<TrackPoint> points, int anchor, int inside,
                                 boolean[] stopEdges, TrackFilterProperties properties, Stats stats) {
        double dwellSeconds = seconds(points.get(anchor), points.get(inside));
        if (dwellSeconds < properties.stopMinDurationSeconds()) {
            return;
        }
        double removedMeters = 0;
        for (int edge = anchor + 1; edge <= inside; edge++) {
            stopEdges[edge] = true;
            removedMeters += TrackDistance.between(points.get(edge - 1), points.get(edge));
        }
        stats.recordStop(dwellSeconds, removedMeters,
                TrackDistance.between(points.get(anchor), points.get(inside)));
    }

    // 상한이 없으면 정확도가 나쁜 곳에서 달리는 사람도 반경 안에 머문 것으로 잡힌다
    private static double radius(TrackPoint point, TrackFilterProperties properties, Stats stats) {
        if (point.accuracyMeters() > properties.stopMaxRadiusMeters()) {
            stats.radiusClampedCount++;
        }
        return Math.clamp(point.accuracyMeters(),
                properties.stopMinRadiusMeters(), properties.stopMaxRadiusMeters());
    }

    // 좌표만으로는 터널을 달린 것과 탈것으로 옮긴 것을 가를 수 없다 — 이 사람이 뛰었다고 보기
    // 자연스러울 때만 인정한다. 칸이 길수록 양 끝으로 낸 평균 속도는 탈것 + 대기로 맞출 수 있어 거리도 묶는다
    private static boolean acceptGap(List<TrackPoint> points, double[] runMeters, double[] runSeconds,
                                     int edge, double meters, double seconds,
                                     TrackFilterProperties properties, Stats stats) {
        double speed = meters / seconds;
        OptionalDouble reference = referenceSpeed(points, runMeters, runSeconds, edge, properties);
        // 출발 직후라 비교할 속도가 없으면 고정 상한을 그대로 쓴다
        double maxSpeed = reference.isPresent()
                ? reference.getAsDouble() * properties.gapSpeedToleranceRatio()
                : properties.gapFallbackMaxSpeedMetersPerSecond();
        boolean accepted = meters <= properties.gapMaxDistanceMeters()
                && speed >= properties.gapMinSpeedMetersPerSecond()
                && speed <= maxSpeed;
        stats.recordGap(meters, seconds, accepted);
        return accepted;
    }

    // 이 칸 직전 일정 시간 동안 평소 칸으로 인정한 칸들의 평균 속도 — 정지·거부·관측 안 된 칸은 빠져
    // 실제로 뛴 속도만 남는다. 산술 평균이 아니라 거리 합 ÷ 시간 합이다
    private static OptionalDouble referenceSpeed(List<TrackPoint> points, double[] runMeters,
                                                 double[] runSeconds, int gapEdge,
                                                 TrackFilterProperties properties) {
        LocalDateTime gapStart = points.get(gapEdge - 1).recordedAt();
        double meters = 0;
        double seconds = 0;
        for (int edge = gapEdge - 1; edge >= 1; edge--) {
            long ago = Duration.between(points.get(edge).recordedAt(), gapStart).toSeconds();
            if (ago > properties.gapReferenceWindowSeconds()) {
                break;
            }
            meters += runMeters[edge];
            seconds += runSeconds[edge];
        }
        return seconds == 0 ? OptionalDouble.empty() : OptionalDouble.of(meters / seconds);
    }

    private static boolean tooFast(TrackPoint from, TrackPoint to, TrackFilterProperties properties) {
        return TrackDistance.between(from, to) / Math.max(seconds(from, to), MIN_EDGE_SECONDS)
                > properties.maxSpeedMetersPerSecond();
    }

    // 시계가 뒤로 간 칸은 0초로 본다 — 누적 시간이 줄면 구간 시간이 음수가 된다
    private static double seconds(TrackPoint from, TrackPoint to) {
        return Math.max(0,
                Duration.between(from.recordedAt(), to.recordedAt()).toMillis() / MILLIS_PER_SECOND);
    }

    private static double pathMeters(List<TrackPoint> points) {
        double meters = 0;
        for (int i = 1; i < points.size(); i++) {
            meters += TrackDistance.between(points.get(i - 1), points.get(i));
        }
        return meters;
    }

    // 판정하면서 쌓는 집계 — 끝나면 불변 요약으로 바꿔 내보낸다
    private static final class Stats {

        double rawMeters;
        double filteredMeters;
        int accuracyDroppedCount;
        double accuracyRemovedMeters;
        int spikePointCount;
        int jumpEdgeCount;
        double spikeRemovedMeters;
        int gapEdgeCount;
        int gapAcceptedCount;
        int gapRejectedCount;
        double gapAcceptedMeters;
        double gapRejectedMeters;
        double maxGapEdgeMeters;
        double maxGapEdgeSeconds;
        int stopCount;
        double stopSeconds;
        double stopRemovedMeters;
        double maxStopSeconds;
        double maxStopDisplacementMeters;
        int radiusClampedCount;

        void recordGap(double meters, double seconds, boolean accepted) {
            gapEdgeCount++;
            if (accepted) {
                gapAcceptedCount++;
                gapAcceptedMeters += meters;
            } else {
                gapRejectedCount++;
                gapRejectedMeters += meters;
            }
            maxGapEdgeMeters = Math.max(maxGapEdgeMeters, meters);
            maxGapEdgeSeconds = Math.max(maxGapEdgeSeconds, seconds);
        }

        void recordStop(double seconds, double removedMeters, double displacementMeters) {
            stopCount++;
            stopSeconds += seconds;
            stopRemovedMeters += removedMeters;
            maxStopSeconds = Math.max(maxStopSeconds, seconds);
            maxStopDisplacementMeters = Math.max(maxStopDisplacementMeters, displacementMeters);
        }

        TrackFilterSummary toSummary() {
            return new TrackFilterSummary(rawMeters, filteredMeters,
                    accuracyDroppedCount, accuracyRemovedMeters,
                    spikePointCount, jumpEdgeCount, spikeRemovedMeters,
                    gapEdgeCount, gapAcceptedCount, gapRejectedCount,
                    gapAcceptedMeters, gapRejectedMeters, maxGapEdgeMeters, maxGapEdgeSeconds,
                    stopCount, stopSeconds, stopRemovedMeters, maxStopSeconds, maxStopDisplacementMeters,
                    radiusClampedCount);
        }
    }
}
