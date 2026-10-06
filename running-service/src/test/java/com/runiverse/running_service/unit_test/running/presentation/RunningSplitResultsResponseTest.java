package com.runiverse.running_service.unit_test.running.presentation;

import com.runiverse.running_service.application.running.port.out.RoutePoint;
import com.runiverse.running_service.application.running.query.split.GetRunningSplitResultsResult;
import com.runiverse.running_service.presentation.running.response.RunningSplitResultsResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("러닝 구간별 결과 응답 변환 단위 테스트")
public class RunningSplitResultsResponseTest {

    private static final long ROOM_ID = 125L;
    private static final UUID USER_ID = UUID.randomUUID();
    private static final LocalDateTime STARTED_AT = LocalDateTime.of(2026, 8, 30, 19, 0, 30);
    private static final LocalDateTime FINISHED_AT = LocalDateTime.of(2026, 8, 30, 19, 30, 30);

    @Test
    @DisplayName("구간 경로는 [위도, 경도] 순서의 두 칸 배열로 나간다")
    void 위도가_먼저다() {
        // given -> 서울 좌표. 위도(37)와 경도(126)가 확연히 달라 뒤집히면 바로 드러난다
        GetRunningSplitResultsResult result = result(List.of(
                new RoutePoint(37.5665, 126.9780),
                new RoutePoint(37.5666, 126.9781)));

        // when
        RunningSplitResultsResponse response = RunningSplitResultsResponse.from(result);

        // then -> GeoJSON은 경도가 먼저라 반대다
        List<double[]> routes = response.splits().get(0).routes();
        assertThat(routes).hasSize(2);
        assertThat(routes.get(0)).containsExactly(37.5665, 126.9780);
        assertThat(routes.get(1)).containsExactly(37.5666, 126.9781);
    }

    @Test
    @DisplayName("도달하지 못한 구간의 경로는 null이다 -> 빈 배열로 바꾸지 않는다")
    void 경로가_없으면_null이다() {
        // given
        GetRunningSplitResultsResult result = result(null);

        // when
        RunningSplitResultsResponse response = RunningSplitResultsResponse.from(result);

        // then
        assertThat(response.splits().get(0).routes()).isNull();
    }

    @Test
    @DisplayName("최상위 값이 자리 그대로 옮겨진다")
    void 최상위_값이_자리_그대로_옮겨진다() {
        // given
        GetRunningSplitResultsResult result = result(List.of(new RoutePoint(37.5665, 126.9780)));

        // when
        RunningSplitResultsResponse response = RunningSplitResultsResponse.from(result);

        // then -> splitDistanceMeters와 totalDistanceMeters가 나란한 정수라 바뀌기 쉽다
        assertThat(response.runningRoomId()).isEqualTo(ROOM_ID);
        assertThat(response.splitDistanceMeters()).isEqualTo(10);
        assertThat(response.totalDistanceMeters()).isEqualTo(5000);
        assertThat(response.totalElevationGainMeters()).isEqualTo(42);
        assertThat(response.startedAt()).isEqualTo(STARTED_AT);
        assertThat(response.finishedAt()).isEqualTo(FINISHED_AT);
    }

    @Test
    @DisplayName("구간과 참가자 지표가 자리 그대로 옮겨진다")
    void 구간_지표가_자리_그대로_옮겨진다() {
        // given -> 값을 전부 다르게 둔다. 같은 값이면 자리가 어긋나도 통과한다
        GetRunningSplitResultsResult result = result(List.of(new RoutePoint(37.5665, 126.9780)));

        // when
        RunningSplitResultsResponse.SplitResponse split =
                RunningSplitResultsResponse.from(result).splits().get(0);

        // then
        assertThat(split.splitNumber()).isEqualTo(1);
        assertThat(split.startDistanceMeters()).isZero();
        assertThat(split.endDistanceMeters()).isEqualTo(10);
        assertThat(split.distanceMeters()).isEqualTo(10);

        RunningSplitResultsResponse.SplitPlayerResponse player = split.players().get(0);
        assertThat(player.userId()).isEqualTo(USER_ID);
        assertThat(player.durationSeconds()).isEqualTo(3);
        assertThat(player.averagePaceSecondsPerKm()).isEqualTo(345);
        assertThat(player.averageCadenceSpm()).isEqualTo(162);
        assertThat(player.caloriesKcal()).isEqualTo(1);
        assertThat(player.elevationChangeMeters()).isNull();
    }

    @Test
    @DisplayName("참가자 메타데이터는 최상위에만 실린다 -> 구간에는 userId와 수치뿐이다")
    void 참가자_메타는_최상위에만_있다() {
        // given
        GetRunningSplitResultsResult result = result(List.of(new RoutePoint(37.5665, 126.9780)));

        // when
        RunningSplitResultsResponse response = RunningSplitResultsResponse.from(result);

        // then -> 구간마다 닉네임·presigned URL을 반복하면 응답이 MB 단위가 된다
        RunningSplitResultsResponse.PlayerResponse player = response.players().get(0);
        assertThat(player.nickname()).isEqualTo("동완러너");
        assertThat(player.profileImageUrl()).isEqualTo("https://cdn.example.com/me.jpg");
        assertThat(player.status()).isEqualTo("COMPLETED");
        assertThat(player.isDeleted()).isFalse();
        assertThat(player.isMe()).isTrue();
        assertThat(RunningSplitResultsResponse.SplitPlayerResponse.class.getRecordComponents())
                .extracting(java.lang.reflect.RecordComponent::getName)
                .containsExactly("userId", "durationSeconds", "averagePaceSecondsPerKm",
                        "averageCadenceSpm", "caloriesKcal", "elevationChangeMeters");
    }

    private GetRunningSplitResultsResult result(List<RoutePoint> routes) {
        return new GetRunningSplitResultsResult(
                ROOM_ID, 10, 5000, 42, STARTED_AT, FINISHED_AT,
                List.of(new GetRunningSplitResultsResult.Player(
                        USER_ID, "동완러너", "https://cdn.example.com/me.jpg",
                        "COMPLETED", false, true)),
                List.of(new GetRunningSplitResultsResult.Split(
                        1, 0, 10, 10, routes,
                        List.of(new GetRunningSplitResultsResult.SplitPlayer(
                                USER_ID, 3, 345, 162, 1, null)))));
    }
}
