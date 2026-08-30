package com.runiverse.running_service.unit_test.running.presentation;

import com.runiverse.running_service.application.running.port.out.RoutePoint;
import com.runiverse.running_service.application.running.query.result.GetRunningResultsResult;
import com.runiverse.running_service.presentation.running.response.RunningResultsResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("러닝 결과 응답 변환 단위 테스트")
public class RunningResultsResponseTest {

    private static final long ROOM_ID = 125L;
    private static final UUID USER_ID = UUID.randomUUID();
    private static final LocalDateTime STARTED_AT = LocalDateTime.of(2026, 8, 30, 19, 0, 30);
    private static final LocalDateTime FINISHED_AT = LocalDateTime.of(2026, 8, 30, 19, 30, 30);

    @Test
    @DisplayName("경로는 [위도, 경도] 순서의 두 칸 배열로 나간다")
    void 위도가_먼저다() {
        // given -> 서울 좌표. 위도(37)와 경도(127)가 확연히 달라 뒤집히면 바로 드러난다
        GetRunningResultsResult result = result(List.of(
                new RoutePoint(37.5665, 126.9780),
                new RoutePoint(37.5666, 126.9781)));

        // when
        RunningResultsResponse response = RunningResultsResponse.from(result);

        // then -> GeoJSON은 경도가 먼저라 반대다. 여기서 뒤집으면 지도가 엉뚱한 곳을 그린다
        assertThat(response.routes()).hasSize(2);
        assertThat(response.routes().get(0)).containsExactly(37.5665, 126.9780);
        assertThat(response.routes().get(1)).containsExactly(37.5666, 126.9781);
    }

    @Test
    @DisplayName("경로가 없으면 null이다 -> 빈 배열로 바꾸지 않는다")
    void 경로가_없으면_null이다() {
        // given -> 본인 기록이 없는 경우다
        GetRunningResultsResult result = result(null);

        // when
        RunningResultsResponse response = RunningResultsResponse.from(result);

        // then -> 빈 배열로 내리면 클라가 "경로 있음"으로 읽는다
        assertThat(response.routes()).isNull();
    }

    @Test
    @DisplayName("최상위 값과 참가자 지표가 자리 그대로 옮겨진다")
    void 필드가_자리_그대로_옮겨진다() {
        // given
        GetRunningResultsResult result = result(List.of(new RoutePoint(37.5665, 126.9780)));

        // when
        RunningResultsResponse response = RunningResultsResponse.from(result);

        // then
        assertThat(response.runningRoomId()).isEqualTo(ROOM_ID);
        assertThat(response.startedAt()).isEqualTo(STARTED_AT);
        assertThat(response.finishedAt()).isEqualTo(FINISHED_AT);

        // 지표가 전부 Integer라 자리가 어긋나도 컴파일이 통과한다 — 값을 다 다르게 두고 확인한다
        RunningResultsResponse.PlayerResponse player = response.players().get(0);
        assertThat(player.userId()).isEqualTo(USER_ID);
        assertThat(player.nickname()).isEqualTo("동완러너");
        assertThat(player.profileImageUrl()).isEqualTo("https://cdn.example.com/me.jpg");
        assertThat(player.status()).isEqualTo("COMPLETED");
        assertThat(player.isDeleted()).isFalse();
        assertThat(player.isMe()).isTrue();
        assertThat(player.totalDistanceMeters()).isEqualTo(5020);
        assertThat(player.totalDurationSeconds()).isEqualTo(1800);
        assertThat(player.totalCaloriesKcal()).isEqualTo(352);
        assertThat(player.averagePaceSecondsPerKm()).isEqualTo(359);
        assertThat(player.averageCadenceSpm()).isEqualTo(165);
        assertThat(player.totalElevationGainMeters()).isEqualTo(42);
    }

    @Test
    @DisplayName("탈퇴·본인 플래그가 뒤바뀌지 않는다")
    void 플래그가_뒤바뀌지_않는다() {
        // given -> 두 boolean이 나란히 있어 순서가 바뀌어도 컴파일이 통과한다
        GetRunningResultsResult result = new GetRunningResultsResult(
                ROOM_ID, STARTED_AT, FINISHED_AT, null,
                List.of(new GetRunningResultsResult.Player(
                        USER_ID, "탈퇴한 사용자", null, "COMPLETED", true, false,
                        5020, 1800, 352, 359, 165, 42)));

        // when
        RunningResultsResponse.PlayerResponse player =
                RunningResultsResponse.from(result).players().get(0);

        // then
        assertThat(player.isDeleted()).isTrue();
        assertThat(player.isMe()).isFalse();
        assertThat(player.nickname()).isEqualTo("탈퇴한 사용자");
        assertThat(player.profileImageUrl()).isNull();
    }

    @Test
    @DisplayName("기록이 없는 참가자는 지표가 전부 null로 옮겨진다")
    void 기록이_없으면_null로_옮겨진다() {
        // given
        GetRunningResultsResult result = new GetRunningResultsResult(
                ROOM_ID, null, null, null,
                List.of(new GetRunningResultsResult.Player(
                        USER_ID, "러닝초보", null, "RUNNING", false, false,
                        null, null, null, null, null, null)));

        // when
        RunningResultsResponse.PlayerResponse player =
                RunningResultsResponse.from(result).players().get(0);

        // then
        assertThat(player.status()).isEqualTo("RUNNING");
        assertThat(player.totalDistanceMeters()).isNull();
        assertThat(player.totalDurationSeconds()).isNull();
        assertThat(player.totalCaloriesKcal()).isNull();
        assertThat(player.averagePaceSecondsPerKm()).isNull();
        assertThat(player.averageCadenceSpm()).isNull();
        assertThat(player.totalElevationGainMeters()).isNull();
    }

    // 지표는 전부 다른 값으로 둔다 — 같은 값이면 자리가 바뀌어도 통과한다
    private GetRunningResultsResult result(List<RoutePoint> routes) {
        return new GetRunningResultsResult(
                ROOM_ID, STARTED_AT, FINISHED_AT, routes,
                List.of(new GetRunningResultsResult.Player(
                        USER_ID, "동완러너", "https://cdn.example.com/me.jpg",
                        "COMPLETED", false, true,
                        5020, 1800, 352, 359, 165, 42)));
    }
}
