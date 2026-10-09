package com.runiverse.running_service.unit_test.running.application;

import com.runiverse.running_service.application.running.common.BoundaryPoint;
import com.runiverse.running_service.application.running.common.PolylineEncoder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@DisplayName("경로 폴리라인 인코더 단위 테스트")
public class PolylineEncoderTest {

    private static final LocalDateTime AT = LocalDateTime.of(2026, 8, 27, 19, 0, 0);
    private static final double PRECISION = 1e-5;

    // 폴리라인은 경계점 목록에서 나온다 — sequence 자리에 경계 거리가 들어간다
    private static BoundaryPoint point(int distanceMeters, double latitude, double longitude) {
        return new BoundaryPoint(distanceMeters, latitude, longitude,
                AT.plusSeconds(distanceMeters), distanceMeters, 0);
    }

    // 인코더와 반대 방향으로 독립 구현해 왕복을 검증한다 — 같은 코드를 두 번 쓰면 검증이 아니다
    private static List<double[]> decode(String encoded) {
        List<double[]> points = new ArrayList<>();
        long latitude = 0;
        long longitude = 0;
        int index = 0;
        while (index < encoded.length()) {
            long[] decodedLatitude = decodeSigned(encoded, index);
            latitude += decodedLatitude[0];
            index = (int) decodedLatitude[1];
            long[] decodedLongitude = decodeSigned(encoded, index);
            longitude += decodedLongitude[0];
            index = (int) decodedLongitude[1];
            points.add(new double[]{latitude / 1e5, longitude / 1e5});
        }
        return points;
    }

    private static long[] decodeSigned(String encoded, int start) {
        long result = 0;
        int shift = 0;
        int index = start;
        int chunk;
        do {
            chunk = encoded.charAt(index++) - 63;
            result |= (long) (chunk & 0x1f) << shift;
            shift += 5;
        } while (chunk >= 0x20);
        long value = (result & 1) != 0 ? ~(result >> 1) : result >> 1;
        return new long[]{value, index};
    }

    @Test
    @DisplayName("구글 레퍼런스 벡터를 그대로 인코딩한다")
    void encodesGoogleReferenceVector() {
        // given -> 직접 구현한 인코더라 외부 기준값에 맞춰두지 않으면
        // 클라이언트 디코더와 어긋나도 알 수 없다
        List<BoundaryPoint> points = List.of(
                point(1, 38.5, -120.2),
                point(2, 40.7, -120.95),
                point(3, 43.252, -126.453));

        // when
        String encoded = PolylineEncoder.encode(points);

        // then
        assertThat(encoded).isEqualTo("_p~iF~ps|U_ulLnnqC_mqNvxq`@");
    }

    @Test
    @DisplayName("좌표가 없으면 빈 문자열이다")
    void emptyTrackEncodesToEmptyString() {
        // when & then
        assertThat(PolylineEncoder.encode(List.of())).isEmpty();
    }

    @Test
    @DisplayName("점이 하나면 첫 좌표만 인코딩한다")
    void singlePointEncodesFirstCoordinateOnly() {
        // when
        String encoded = PolylineEncoder.encode(List.of(point(1, 38.5, -120.2)));

        // then
        assertThat(encoded).isEqualTo("_p~iF~ps|U");
    }

    @Test
    @DisplayName("남서 방향으로 움직여도 왕복이 유지된다")
    void handlesNegativeDeltas() {
        // given -> 위도·경도가 모두 줄어드는 경로
        List<BoundaryPoint> points = List.of(
                point(1, 37.51234, 127.02345),
                point(2, 37.51000, 127.02000),
                point(3, 37.50777, 127.01888));

        // when
        List<double[]> decoded = decode(PolylineEncoder.encode(points));

        // then
        assertThat(decoded).hasSize(3);
        for (int i = 0; i < points.size(); i++) {
            assertThat(decoded.get(i)[0]).isCloseTo(points.get(i).latitude(), within(PRECISION));
            assertThat(decoded.get(i)[1]).isCloseTo(points.get(i).longitude(), within(PRECISION));
        }
    }

    @Test
    @DisplayName("반올림을 차분보다 먼저 해서 오차가 누적되지 않는다")
    void roundsBeforeDiffingSoErrorDoesNotAccumulate() {
        // given -> 5자리 아래에서만 움직이는 점들.
        // 차분을 먼저 내고 반올림하면 매번 0이 되어 경로가 시작점에 붙어버린다
        List<BoundaryPoint> points = List.of(
                point(1, 37.000004, 127.0),
                point(2, 37.000008, 127.0),
                point(3, 37.000012, 127.0));

        // when
        List<double[]> decoded = decode(PolylineEncoder.encode(points));

        // then -> 각 좌표를 따로 반올림한 값(3700000, 3700001, 3700001)이 나와야 한다
        assertThat(decoded.get(0)[0]).isCloseTo(37.00000, within(1e-9));
        assertThat(decoded.get(1)[0]).isCloseTo(37.00001, within(1e-9));
        assertThat(decoded.get(2)[0]).isCloseTo(37.00001, within(1e-9));
    }

    @Test
    @DisplayName("긴 트랙도 마지막 점까지 좌표가 보존된다")
    void preservesEveryPointInLongTrack() {
        // given -> 오차가 쌓이면 뒤로 갈수록 벌어진다
        List<BoundaryPoint> points = new ArrayList<>();
        for (int i = 0; i < 500; i++) {
            points.add(point(i, 37.5 + i * 0.00013, 127.0 + i * 0.00017));
        }

        // when
        List<double[]> decoded = decode(PolylineEncoder.encode(points));

        // then
        assertThat(decoded).hasSize(500);
        assertThat(decoded.get(499)[0]).isCloseTo(points.get(499).latitude(), within(PRECISION));
        assertThat(decoded.get(499)[1]).isCloseTo(points.get(499).longitude(), within(PRECISION));
    }
}
