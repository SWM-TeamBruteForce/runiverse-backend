package com.runiverse.running_service.application.running.common;

import java.util.ArrayList;
import java.util.List;

// 경계점 목록을 끊긴 곳에서 조각으로 나눈다 — 조각을 순서대로 이으면 원래 목록과 같다.
// 점을 더하거나 빼지 않으므로 route_polyline의 점 번호(구간 인덱스·콤보 점)는 그대로다
public final class RouteSegmenter {

    private RouteSegmenter() {
    }

    public static List<List<BoundaryPoint>> split(List<BoundaryPoint> boundaries,
                                                  boolean[] breakEdges) {
        List<List<BoundaryPoint>> segments = new ArrayList<>();
        List<BoundaryPoint> current = new ArrayList<>();
        for (int k = 0; k < boundaries.size(); k++) {
            if (k > 0 && brokenBetween(boundaries.get(k - 1), boundaries.get(k), breakEdges)) {
                segments.add(current);
                current = new ArrayList<>();
            }
            current.add(boundaries.get(k));
        }
        if (!current.isEmpty()) {
            segments.add(current);
        }
        return segments;
    }

    // 경계점은 칸 sourceIndex + 1 위에 놓인다(TrackResampler). 두 경계점이 놓인 칸과 그 사이 칸 중
    // 하나라도 끊는 칸이면 끊긴다 — 첫 칸이 0이면 0m 경계점이 그 칸의 시작점에 붙어 이 범위에 들어온다
    private static boolean brokenBetween(BoundaryPoint from, BoundaryPoint to,
                                         boolean[] breakEdges) {
        for (int edge = from.sourceIndex() + 1; edge <= to.sourceIndex() + 1; edge++) {
            if (breakEdges[edge]) {
                return true;
            }
        }
        return false;
    }
}
