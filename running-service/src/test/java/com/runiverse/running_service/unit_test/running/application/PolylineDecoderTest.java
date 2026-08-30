package com.runiverse.running_service.unit_test.running.application;

import com.runiverse.running_service.application.running.command.finish.BoundaryPoint;
import com.runiverse.running_service.application.running.command.finish.PolylineDecoder;
import com.runiverse.running_service.application.running.command.finish.PolylineEncoder;
import com.runiverse.running_service.application.running.port.out.RoutePoint;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@DisplayName("경로 폴리라인 디코더 단위 테스트")
public class PolylineDecoderTest {

    private static final LocalDateTime AT = LocalDateTime.of(2026, 8, 30, 19, 0, 0);
    // 인코더가 소수점 5자리에서 반올림한다 — 왕복 오차의 상한이다
    private static final double PRECISION = 1e-5;

    @Test
    @DisplayName("인코더가 만든 문자열을 되풀면 원래 좌표가 나온다")
    void 왕복하면_원래_좌표가_나온다() {
        // given -> 10m 간격 경계점을 흉내낸다
        List<BoundaryPoint> original = List.of(
                point(0, 35.1795543, 129.0756416),
                point(10, 35.1796012, 129.0757104),
                point(20, 35.1796481, 129.0757792),
                point(30, 35.1842012, 129.0831421));

        // when
        List<RoutePoint> decoded = PolylineDecoder.decode(PolylineEncoder.encode(original));

        // then
        assertThat(decoded).hasSize(original.size());
        for (int index = 0; index < original.size(); index++) {
            assertThat(decoded.get(index).latitude())
                    .isCloseTo(original.get(index).latitude(), within(PRECISION));
            assertThat(decoded.get(index).longitude())
                    .isCloseTo(original.get(index).longitude(), within(PRECISION));
        }
    }

    @Test
    @DisplayName("음수 좌표도 왕복한다 -> 부호 비트를 되돌리지 못하면 여기서 깨진다")
    void 음수_좌표도_왕복한다() {
        // given -> 남위·서경
        List<BoundaryPoint> original = List.of(
                point(0, -33.8688197, -151.2092955),
                point(10, -33.8689012, -151.2093644));

        // when
        List<RoutePoint> decoded = PolylineDecoder.decode(PolylineEncoder.encode(original));

        // then
        assertThat(decoded.get(0).latitude()).isCloseTo(-33.8688197, within(PRECISION));
        assertThat(decoded.get(0).longitude()).isCloseTo(-151.2092955, within(PRECISION));
        assertThat(decoded.get(1).latitude()).isCloseTo(-33.8689012, within(PRECISION));
        assertThat(decoded.get(1).longitude()).isCloseTo(-151.2093644, within(PRECISION));
    }

    @Test
    @DisplayName("점 하나짜리 경로도 왕복한다")
    void 점_하나도_왕복한다() {
        // given
        List<BoundaryPoint> original = List.of(point(0, 35.1795543, 129.0756416));

        // when
        List<RoutePoint> decoded = PolylineDecoder.decode(PolylineEncoder.encode(original));

        // then
        assertThat(decoded).hasSize(1);
        assertThat(decoded.get(0).latitude()).isCloseTo(35.1795543, within(PRECISION));
    }

    @Test
    @DisplayName("빈 문자열은 빈 목록이 된다 -> 예외로 종료 흐름을 막지 않는다")
    void 빈_문자열은_빈_목록이다() {
        // when & then
        assertThat(PolylineDecoder.decode("")).isEmpty();
    }

    // 인코더는 위경도만 쓴다 — 나머지 값은 왕복에 영향을 주지 않는다
    private BoundaryPoint point(int distanceMeters, double latitude, double longitude) {
        return new BoundaryPoint(distanceMeters, latitude, longitude, AT, 0);
    }
}
