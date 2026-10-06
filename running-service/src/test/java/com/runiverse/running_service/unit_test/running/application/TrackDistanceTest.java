package com.runiverse.running_service.unit_test.running.application;

import com.runiverse.running_service.application.running.common.TrackDistance;
import com.runiverse.running_service.application.running.port.out.TrackPoint;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@DisplayName("트랙 거리 계산 단위 테스트")
public class TrackDistanceTest {

    private static final LocalDateTime AT = LocalDateTime.of(2026, 8, 27, 19, 0, 0);

    private static TrackPoint point(long sequence, double latitude, double longitude) {
        return new TrackPoint(sequence, latitude, longitude,
                null, 5.0, null, null, null, null, AT.plusSeconds(sequence));
    }

    @Test
    @DisplayName("알려진 두 지점 사이 거리를 낸다")
    void measuresKnownDistance() {
        // given -> 서울시청 ~ 부산시청, 실제 대권거리 약 325km
        TrackPoint seoul = point(1, 37.566535, 126.977969);
        TrackPoint busan = point(2, 35.179554, 129.075642);

        // when
        double meters = TrackDistance.between(seoul, busan);

        // then -> 공식이나 지구 반경을 잘못 넣으면 여기서 크게 벌어진다
        assertThat(meters).isCloseTo(325_000, within(3_000.0));
    }

    @Test
    @DisplayName("짧은 구간도 미터 단위로 정확하다")
    void measuresShortSegmentAccurately() {
        // given -> 위도 0.0009° ≈ 100.2m (러닝은 짧은 구간의 누적이라 이쪽이 실제로 중요하다)
        TrackPoint from = point(1, 37.50000, 127.00000);
        TrackPoint to = point(2, 37.50090, 127.00000);

        // when & then
        assertThat(TrackDistance.between(from, to)).isCloseTo(100.2, within(0.5));
    }

    @Test
    @DisplayName("경도 1도의 실제 길이는 위도에 따라 달라진다")
    void longitudeDegreeShrinksWithLatitude() {
        // given -> 같은 경도차를 적도와 위도 60°에서 잰다
        double equator = TrackDistance.between(
                point(1, 0.0, 127.0), point(2, 0.0, 127.01));
        double high = TrackDistance.between(
                point(1, 60.0, 127.0), point(2, 60.0, 127.01));

        // then -> cos(60°)=0.5라 절반이 된다. 평면 계산으로 바꾸면 이 테스트가 깨진다
        assertThat(high).isCloseTo(equator / 2, within(equator * 0.01));
    }

    @Test
    @DisplayName("누적 배열은 첫 원소가 0이고 점 개수와 길이가 같다")
    void cumulativeStartsAtZeroAndMatchesPointCount() {
        // given
        List<TrackPoint> points = List.of(
                point(1, 37.50000, 127.00000),
                point(2, 37.50002, 127.00003),
                point(3, 37.50005, 127.00007));

        // when
        double[] cumulative = TrackDistance.cumulativeMeters(points);

        // then -> 10m 경계 탐색이 인덱스와 점을 1:1로 맞춰 쓴다
        assertThat(cumulative).hasSize(3);
        assertThat(cumulative[0]).isZero();
    }

    @Test
    @DisplayName("누적 배열은 단조 증가한다")
    void cumulativeIsMonotonic() {
        // given -> 방향이 바뀌어도 거리는 줄지 않는다
        List<TrackPoint> points = List.of(
                point(1, 37.50000, 127.00000),
                point(2, 37.50050, 127.00050),
                point(3, 37.50020, 127.00010),
                point(4, 37.50080, 127.00090));

        // when
        double[] cumulative = TrackDistance.cumulativeMeters(points);

        // then -> 여기가 깨지면 10m 경계 탐색이 앞으로만 훑는 전제가 무너진다
        for (int i = 1; i < cumulative.length; i++) {
            assertThat(cumulative[i]).isGreaterThan(cumulative[i - 1]);
        }
    }

    @Test
    @DisplayName("누적 배열의 마지막 원소가 총거리다")
    void lastElementIsTotalDistance() {
        // given -> 100m 구간 세 개를 이어 붙인다
        List<TrackPoint> points = List.of(
                point(1, 37.50000, 127.0),
                point(2, 37.50090, 127.0),
                point(3, 37.50180, 127.0),
                point(4, 37.50270, 127.0));

        // when
        double[] cumulative = TrackDistance.cumulativeMeters(points);

        // then
        assertThat(cumulative[cumulative.length - 1]).isCloseTo(300.6, within(1.0));
    }

    @Test
    @DisplayName("좌표가 없거나 하나뿐이면 거리가 0이다")
    void emptyOrSinglePointHasNoDistance() {
        // when & then -> 기록을 만들 수 없는 트랙으로 흘러가는 경로다
        assertThat(TrackDistance.cumulativeMeters(List.of())).isEmpty();
        assertThat(TrackDistance.cumulativeMeters(List.of(point(1, 37.5, 127.0))))
                .containsExactly(0.0);
    }

    @Test
    @DisplayName("같은 자리에 머물면 거리가 늘지 않는다")
    void stationaryPointsAddNoDistance() {
        // given -> 일시정지 없이 신호 대기로 제자리에 있는 경우
        List<TrackPoint> points = List.of(
                point(1, 37.50000, 127.00000),
                point(2, 37.50000, 127.00000),
                point(3, 37.50000, 127.00000));

        // when
        double[] cumulative = TrackDistance.cumulativeMeters(points);

        // then
        assertThat(cumulative[2]).isZero();
    }

    @Test
    @DisplayName("긴 트랙에서도 구간 합과 누적이 어긋나지 않는다")
    void cumulativeMatchesSumOfSegments() {
        // given -> 1,800점 (30분 러닝 규모)
        List<TrackPoint> points = new ArrayList<>();
        for (int i = 0; i < 1_800; i++) {
            points.add(point(i, 37.5 + i * 0.000025, 127.0 + i * 0.000018));
        }

        // when
        double[] cumulative = TrackDistance.cumulativeMeters(points);
        double sum = 0;
        for (int i = 1; i < points.size(); i++) {
            sum += TrackDistance.between(points.get(i - 1), points.get(i));
        }

        // then -> 구간 거리는 cumulative 뺄셈으로 꺼내 쓰므로 둘이 같아야 한다
        assertThat(cumulative[cumulative.length - 1]).isCloseTo(sum, within(1e-6));
    }
}
