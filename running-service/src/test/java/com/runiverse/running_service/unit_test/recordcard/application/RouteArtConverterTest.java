package com.runiverse.running_service.unit_test.recordcard.application;

import com.runiverse.running_service.application.recordcard.common.RouteArtConverter;
import com.runiverse.running_service.application.recordcard.common.RouteArtConverter.Point;
import com.runiverse.running_service.application.running.common.PolylineDecoder;
import com.runiverse.running_service.application.running.common.PolylineEncoder;
import com.runiverse.running_service.application.running.port.out.RoutePoint;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

@DisplayName("경로 선 아트 변환 단위 테스트")
class RouteArtConverterTest {

    @Test
    @DisplayName("구간이 없거나 모두 비었으면 빈 목록이다")
    void empty() {
        assertThat(RouteArtConverter.convert(List.of())).isEmpty();
        assertThat(RouteArtConverter.convert(List.of(""))).isEmpty();
    }

    @Test
    @DisplayName("남북으로 긴 경로는 세로가 0~1을 채우고 가로는 가운데다 — 북쪽이 위(y=0)다")
    void northSouth() {
        // given — 같은 경도에서 북쪽으로 0.01도
        String polyline = encode(new RoutePoint(37.50, 127.00), new RoutePoint(37.51, 127.00));

        // when
        List<Point> segment = RouteArtConverter.convert(List.of(polyline)).getFirst();

        // then
        assertThat(segment).containsExactly(new Point(0.5, 1.0), new Point(0.5, 0.0));
    }

    @Test
    @DisplayName("동서로 긴 경로는 가로가 0~1을 채우고 세로는 가운데다")
    void eastWest() {
        // given — 적도에서는 경도 보정(cos)이 1이다
        String polyline = encode(new RoutePoint(0.0, 127.00), new RoutePoint(0.0, 127.01));

        // when
        List<Point> segment = RouteArtConverter.convert(List.of(polyline)).getFirst();

        // then
        assertThat(segment).containsExactly(new Point(0.0, 0.5), new Point(1.0, 0.5));
    }

    @Test
    @DisplayName("경도는 위도의 cos만큼 줄여 실제 거리 비율을 지킨다 — 북위 60도에서 경도 0.02도는 위도 0.01도와 같은 길이다")
    void longitudeScaledByLatitude() {
        // given — 남서 → 북동 대각선
        String polyline = encode(new RoutePoint(60.00, 10.00), new RoutePoint(60.01, 10.02));

        // when
        List<Point> segment = RouteArtConverter.convert(List.of(polyline)).getFirst();

        // then — 가로·세로가 거의 같아 정사각형의 두 모서리에 놓인다
        assertThat(segment.get(0).x()).isCloseTo(0.0, within(0.001));
        assertThat(segment.get(0).y()).isCloseTo(1.0, within(0.001));
        assertThat(segment.get(1).x()).isCloseTo(1.0, within(0.001));
        assertThat(segment.get(1).y()).isCloseTo(0.0, within(0.001));
    }

    @Test
    @DisplayName("한 점에 머문 경로는 크기가 없어 가운데 점이다")
    void zeroSpan() {
        // given
        String polyline = encode(new RoutePoint(37.5, 127.0), new RoutePoint(37.5, 127.0));

        // when & then
        assertThat(RouteArtConverter.convert(List.of(polyline)).getFirst())
                .containsOnly(new Point(0.5, 0.5));
    }

    @Test
    @DisplayName("조각은 잇지 않고 따로 둔다 — 순서도 그대로다")
    void keepsSegments() {
        // given — 적도 근처라 가로·세로 0.01도가 같은 길이다(정사각형)
        String first = encode(new RoutePoint(0.00, 127.00), new RoutePoint(0.01, 127.00));
        String second = encode(new RoutePoint(0.01, 127.01), new RoutePoint(0.00, 127.01));

        // when
        List<List<Point>> art = RouteArtConverter.convert(List.of(first, second));

        // then
        assertThat(art).hasSize(2);
        assertThat(art.get(0).getFirst()).isEqualTo(new Point(0.0, 1.0));
        assertThat(art.get(1).getLast()).isEqualTo(new Point(1.0, 1.0));
    }

    @Test
    @DisplayName("점이 200개를 넘으면 조각 크기에 비례해 200개로 줄이고, 조각마다 첫·끝 점은 남긴다")
    void reducesToMaxPoints() {
        // given — 300점 + 100점
        String first = encode(line(37.50, 300));
        String second = encode(line(37.60, 100));
        List<List<Point>> full = convertWithoutLimit(first, second);

        // when
        List<List<Point>> art = RouteArtConverter.convert(List.of(first, second));

        // then
        assertThat(art.get(0)).hasSize(149);
        assertThat(art.get(1)).hasSize(51);
        assertThat(art.get(0).getFirst()).isEqualTo(full.get(0).getFirst());
        assertThat(art.get(0).getLast()).isEqualTo(full.get(0).getLast());
        assertThat(art.get(1).getFirst()).isEqualTo(full.get(1).getFirst());
        assertThat(art.get(1).getLast()).isEqualTo(full.get(1).getLast());
    }

    @Test
    @DisplayName("상한을 정하면 그만큼만 남긴다 — 기록 목록의 달력 칸은 50개, 조각마다 첫·끝 점은 그대로다")
    void reducesToGivenLimit() {
        // given — 300점 + 100점
        String first = encode(line(37.50, 300));
        String second = encode(line(37.60, 100));
        List<List<Point>> full = convertWithoutLimit(first, second);

        // when
        List<List<Point>> art = RouteArtConverter.convert(List.of(first, second), 50);

        // then
        assertThat(art.get(0)).hasSize(36);
        assertThat(art.get(1)).hasSize(14);
        assertThat(art.stream().mapToInt(List::size).sum()).isLessThanOrEqualTo(50);
        assertThat(art.get(0).getFirst()).isEqualTo(full.get(0).getFirst());
        assertThat(art.get(1).getLast()).isEqualTo(full.get(1).getLast());
    }

    @Test
    @DisplayName("짧은 조각이 많이 섞여도 200개를 넘지 않는다 — 짧은 조각의 첫·끝을 먼저 남기고 나머지를 긴 조각이 쓴다")
    void mixedSegmentsStayWithinMaxPoints() {
        // given — 1000점 + 2점짜리 조각 50개(1100점)
        List<String> polylines = new ArrayList<>(List.of(encode(line(37.50, 1000))));
        for (int i = 0; i < 50; i++) {
            polylines.add(encode(line(37.60 + i * 0.001, 2)));
        }

        // when
        List<List<Point>> art = RouteArtConverter.convert(polylines);

        // then
        assertThat(art.stream().mapToInt(List::size).sum()).isLessThanOrEqualTo(200);
        assertThat(art.getFirst()).hasSize(100);
        assertThat(art.subList(1, art.size())).allSatisfy(segment -> assertThat(segment).hasSize(2));
    }

    @Test
    @DisplayName("짧은 조각이 섞여도 달력 칸은 50개를 넘지 않는다")
    void mixedSegmentsStayWithinGivenLimit() {
        // given — 1000점 + 2점짜리 조각 10개(1020점)
        List<String> polylines = new ArrayList<>(List.of(encode(line(37.50, 1000))));
        for (int i = 0; i < 10; i++) {
            polylines.add(encode(line(37.60 + i * 0.001, 2)));
        }

        // when
        List<List<Point>> art = RouteArtConverter.convert(polylines, 50);

        // then
        assertThat(art.stream().mapToInt(List::size).sum()).isLessThanOrEqualTo(50);
        assertThat(art.getFirst()).hasSize(30);
        assertThat(art.subList(1, art.size())).allSatisfy(segment -> assertThat(segment).hasSize(2));
    }

    @Test
    @DisplayName("조각마다 나눈 몫의 끝수를 버리지 않는다 — 짧은 왕복 조각이 많아도 상한을 다 써 반환점을 남긴다")
    void keepsTurnaroundsOfShortRoundTrips() {
        // given — 3점 왕복 조각 10개 + 5점 왕복 조각 10개(80점), 조각마다 첫 점과 끝 점이 같다
        List<String> polylines = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            polylines.add(encode(roundTrip(37.60 + i * 0.01, 3)));
        }
        for (int i = 0; i < 10; i++) {
            polylines.add(encode(roundTrip(37.80 + i * 0.01, 5)));
        }

        // when
        List<List<Point>> art = RouteArtConverter.convert(polylines, 50);

        // then — 첫·끝만 남은 왕복 조각은 점 하나로 보인다
        assertThat(art).hasSize(20);
        assertThat(art.stream().mapToInt(List::size).sum()).isEqualTo(50);
        assertThat(art.stream()
                .filter(segment -> segment.stream().anyMatch(point -> !point.equals(segment.getFirst())))
                .count()).isEqualTo(10L);
    }

    @Test
    @DisplayName("상한 아래의 경로는 상한과 상관없이 그대로다 — 기본 변환과 같다")
    void underLimitIsSameAsDefault() {
        // given
        String polyline = encode(line(37.50, 40));

        // when & then
        assertThat(RouteArtConverter.convert(List.of(polyline), 50))
                .isEqualTo(RouteArtConverter.convert(List.of(polyline)));
    }

    @Test
    @DisplayName("상한이 2보다 작으면 조각의 첫·끝을 남길 수 없어 거부한다")
    void rejectsTooSmallLimit() {
        assertThatThrownBy(() -> RouteArtConverter.convert(List.of(encode(line(37.50, 10))), 1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("조각이 아주 많아도 조각마다 첫·끝 점은 남긴다")
    void manySegmentsKeepEnds() {
        // given — 2점짜리 조각 150개(300점)
        List<String> polylines = new ArrayList<>();
        for (int i = 0; i < 150; i++) {
            polylines.add(encode(new RoutePoint(37.50 + i * 0.001, 127.00), new RoutePoint(37.50 + i * 0.001, 127.01)));
        }

        // when
        List<List<Point>> art = RouteArtConverter.convert(polylines);

        // then
        assertThat(art).hasSize(150).allSatisfy(segment -> assertThat(segment).hasSize(2));
    }

    @Test
    @DisplayName("모든 좌표는 0~1 사이이고 소수점 넷째 자리까지다")
    void rangeAndPrecision() {
        // given — 비스듬한 곡선
        List<RoutePoint> points = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            points.add(new RoutePoint(37.50 + i * 0.0003, 127.00 + Math.sin(i / 5.0) * 0.004));
        }

        // when
        List<Point> segment = RouteArtConverter.convert(List.of(PolylineEncoder.encodePoints(points))).getFirst();

        // then
        assertThat(segment).allSatisfy(point -> {
            assertThat(point.x()).isBetween(0.0, 1.0);
            assertThat(point.y()).isBetween(0.0, 1.0);
            assertThat(BigDecimal.valueOf(point.x()).scale()).isLessThanOrEqualTo(4);
            assertThat(BigDecimal.valueOf(point.y()).scale()).isLessThanOrEqualTo(4);
        });
    }

    private static String encode(RoutePoint... points) {
        return PolylineEncoder.encodePoints(List.of(points));
    }

    private static String encode(List<RoutePoint> points) {
        return PolylineEncoder.encodePoints(points);
    }

    // 북쪽으로 곧게 뻗은 count개 점
    private static List<RoutePoint> line(double startLatitude, int count) {
        List<RoutePoint> points = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            points.add(new RoutePoint(startLatitude + i * 0.0001, 127.00 + i * 0.00005));
        }
        return points;
    }

    // 북쪽으로 갔다가 같은 자리로 돌아오는 count개 점 — 첫 점과 끝 점이 같다
    private static List<RoutePoint> roundTrip(double startLatitude, int count) {
        List<RoutePoint> points = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            points.add(new RoutePoint(startLatitude + Math.min(i, count - 1 - i) * 0.0001, 127.00));
        }
        return points;
    }

    // 줄이기 전의 첫·끝 좌표 — 경로가 곧게 뻗어 첫·끝 점만으로도 감싸는 사각형이 같아 좌표 기준이 같다
    private static List<List<Point>> convertWithoutLimit(String first, String second) {
        return RouteArtConverter.convert(List.of(ends(first), ends(second)));
    }

    private static String ends(String polyline) {
        List<RoutePoint> points = PolylineDecoder.decode(polyline);
        return PolylineEncoder.encodePoints(List.of(points.getFirst(), points.getLast()));
    }
}
