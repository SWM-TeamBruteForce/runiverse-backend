package com.runiverse.running_service.application.recordcard.common;

import com.runiverse.running_service.application.running.common.PolylineDecoder;
import com.runiverse.running_service.application.running.port.out.RoutePoint;

import java.util.ArrayList;
import java.util.List;

// 기록의 구간 경로를 카드용 선 아트로 바꾼다 — 실제 위치를 지우고 모양만 남긴다.
// 카드는 남에게도 보이므로 위경도를 싣지 않는다. 보는 사람이 본인이어도 같다 — 본인의 실제 경로는 6-1이 준다
public final class RouteArtConverter {

    // 상세(8-1)의 전체 조각을 합친 점 수 상한 — 칸 하나에 선 하나를 긋기에 충분하고 응답이 작다
    static final int MAX_POINTS = 200;
    private static final double PRECISION = 10_000.0;   // 소수점 넷째 자리
    private static final double CENTER = 0.5;

    private RouteArtConverter() {

    }

    // 구간 순서(segment_number)대로 받은 폴리라인을 조각별 0~1 좌표로 바꾼다. 경로가 없으면 빈 목록이다.
    // 점은 상세(8-1) 상한(200개)까지 남긴다
    public static List<List<Point>> convert(List<String> segmentPolylines) {
        return convert(segmentPolylines, MAX_POINTS);
    }

    // 점 수 상한을 정해 바꾼다 — 기록 목록(7-1)의 달력 칸처럼 작게 그릴 때 점을 더 줄인다
    public static List<List<Point>> convert(List<String> segmentPolylines, int maxPoints) {
        if (maxPoints < 2) {
            throw new IllegalArgumentException("선 아트 점 수 상한은 2 이상이어야 한다");
        }
        List<List<RoutePoint>> segments = segmentPolylines.stream()
                .map(PolylineDecoder::decode)
                .filter(points -> !points.isEmpty())
                .toList();
        if (segments.isEmpty()) {
            return List.of();
        }

        // 1. 경로 전체를 감싸는 사각형
        double minLatitude = Double.MAX_VALUE;
        double maxLatitude = -Double.MAX_VALUE;
        double minLongitude = Double.MAX_VALUE;
        double maxLongitude = -Double.MAX_VALUE;
        for (List<RoutePoint> segment : segments) {
            for (RoutePoint point : segment) {
                minLatitude = Math.min(minLatitude, point.latitude());
                maxLatitude = Math.max(maxLatitude, point.latitude());
                minLongitude = Math.min(minLongitude, point.longitude());
                maxLongitude = Math.max(maxLongitude, point.longitude());
            }
        }

        // 2. 경도 1도는 위도가 높을수록 짧다 — 중심 위도의 cos을 곱해 가로·세로를 같은 거리 비율로 맞춘다
        double longitudeScale = Math.cos(Math.toRadians((minLatitude + maxLatitude) / 2));
        double width = (maxLongitude - minLongitude) * longitudeScale;
        double height = maxLatitude - minLatitude;
        double span = Math.max(width, height);

        // 3. 긴 변이 0~1을 채우고 짧은 변은 가운데 놓인다. y는 북쪽이 위(화면 좌표라 남쪽으로 커진다)
        double xOffset = (span - width) / 2;
        double yOffset = (span - height) / 2;
        List<List<Point>> art = new ArrayList<>();
        for (List<RoutePoint> segment : reduce(segments, maxPoints)) {
            List<Point> points = new ArrayList<>(segment.size());
            for (RoutePoint point : segment) {
                if (span == 0) {
                    // 한 점에 머문 경로 — 크기가 없어 가운데 하나로 둔다
                    points.add(new Point(CENTER, CENTER));
                    continue;
                }
                double x = ((point.longitude() - minLongitude) * longitudeScale + xOffset) / span;
                double y = ((maxLatitude - point.latitude()) + yOffset) / span;
                points.add(new Point(round(x), round(y)));
            }
            art.add(points);
        }
        return art;
    }

    // 점이 상한을 넘으면 조각마다 첫·끝 점(시작·도착·끊긴 곳)을 먼저 남기고, 남은 몫을 조각의 나머지 점 수에 비례해 나눈다.
    // 몫은 앞 조각부터 누적해 내림한다 — 조각마다 버린 끝수가 다음 조각으로 넘어가 남은 몫을 다 쓴다.
    // 조각이 아주 많아 첫·끝만으로도 상한을 넘으면 첫·끝만 남긴다
    private static List<List<RoutePoint>> reduce(List<List<RoutePoint>> segments, int maxPoints) {
        int total = segments.stream().mapToInt(List::size).sum();
        if (total <= maxPoints) {
            return segments;
        }
        int ends = segments.stream().mapToInt(RouteArtConverter::ends).sum();
        int spare = Math.max(maxPoints - ends, 0);
        int middles = total - ends;
        List<List<RoutePoint>> reduced = new ArrayList<>(segments.size());
        long counted = 0;
        int given = 0;
        for (List<RoutePoint> segment : segments) {
            int keep = ends(segment);
            counted += segment.size() - keep;
            int upTo = spare == 0 ? 0 : (int) (counted * spare / middles);
            reduced.add(sample(segment, keep + upTo - given));
            given = upTo;
        }
        return reduced;
    }

    // 조각에서 늘 남기는 점 수 — 첫·끝 둘(점이 하나뿐인 조각은 하나)
    private static int ends(List<RoutePoint> segment) {
        return Math.min(segment.size(), 2);
    }

    // 첫 점부터 끝 점까지 같은 간격으로 budget개를 고른다
    private static List<RoutePoint> sample(List<RoutePoint> segment, int budget) {
        if (segment.size() <= budget) {
            return segment;
        }
        if (budget == 1) {
            return List.of(segment.getFirst());
        }
        List<RoutePoint> sampled = new ArrayList<>(budget);
        int last = segment.size() - 1;
        for (int i = 0; i < budget; i++) {
            sampled.add(segment.get((int) Math.round((double) i * last / (budget - 1))));
        }
        return sampled;
    }

    private static double round(double value) {
        return Math.round(value * PRECISION) / PRECISION;
    }

    // 선 아트의 점 하나 — x는 동쪽으로, y는 남쪽으로 커진다(0~1)
    public record Point(double x, double y) {

    }
}
