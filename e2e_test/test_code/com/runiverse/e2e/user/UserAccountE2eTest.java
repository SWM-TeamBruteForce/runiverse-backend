package com.runiverse.e2e.user;

import com.runiverse.e2e.E2eTestSupport;
import com.runiverse.e2e.RunningWebSocket;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("배포 이미지 대상 설정·회원탈퇴 E2E 테스트")
class UserAccountE2eTest extends E2eTestSupport {

    private static final ZoneId APP_ZONE = ZoneId.of("Asia/Seoul");   // 좌표 시각은 오프셋 없는 앱 시각이다

    @Test
    @DisplayName("설정은 기본값으로 시작하고, 한 필드만 바꿔도 전체 설정이 돌아온다")
    void settingsArePartiallyChanged() {
        // given
        TestUser user = signUpAndOnboard();
        Response initial = get("/users/me/settings", user.accessToken());
        assertThat(initial.status()).isEqualTo(200);
        assertThat(initial.bool("alertConsent")).isTrue();
        assertThat(initial.text("profileVisibility")).isEqualTo("PUBLIC");

        // when - 알림만 끈다
        Response changed = patch("/users/me/settings",
                Map.of("alertConsent", false), user.accessToken());

        // then - 보내지 않은 공개 범위도 응답에 담긴다
        assertThat(changed.status()).isEqualTo(200);
        assertThat(changed.bool("alertConsent")).isFalse();
        assertThat(changed.text("profileVisibility")).isEqualTo("PUBLIC");
        // 공개 범위만 바꿔도 앞서 끈 알림이 되살아나지 않는다
        Response visibility = patch("/users/me/settings",
                Map.of("profileVisibility", "FRIENDS"), user.accessToken());
        assertThat(visibility.bool("alertConsent")).isFalse();
        assertThat(visibility.text("profileVisibility")).isEqualTo("FRIENDS");
        Response stored = get("/users/me/settings", user.accessToken());
        assertThat(stored.bool("alertConsent")).isFalse();
        assertThat(stored.text("profileVisibility")).isEqualTo("FRIENDS");
    }

    @Test
    @DisplayName("지원하지 않는 공개 범위는 400으로 걸리고 저장되지 않는다")
    void unsupportedVisibilityIsRejected() {
        // given
        TestUser user = signUpAndOnboard();

        // when
        Response response = patch("/users/me/settings", Map.of(
                "alertConsent", false,
                "profileVisibility", "PRIVATE"
        ), user.accessToken());

        // then - 함께 보낸 알림도 바뀌지 않는다
        assertThat(response.status()).isEqualTo(400);
        assertThat(response.text("code")).isEqualTo("INVALID_REQUEST");
        assertThat(get("/users/me/settings", user.accessToken()).bool("alertConsent")).isTrue();
    }

    @Test
    @DisplayName("탈퇴하면 토큰이 즉시 막히고 다시 로그인할 수 없으며 남에게는 없는 사용자다")
    void accountIsDeleted() {
        // given - 리프레시 토큰도 무효가 되는지 보려고 로그인으로 한 벌 더 받아 둔다
        TestUser user = signUpAndOnboard();
        TestUser viewer = signUpAndOnboard();
        Response loggedIn = post("/auth/login",
                Map.of("email", user.email(), "password", user.password()));
        assertThat(loggedIn.status()).isEqualTo(200);

        // when
        Response deleted = delete("/users/me", user.accessToken());

        // then
        assertThat(deleted.status()).isEqualTo(204);
        Response reused = get("/users/me", user.accessToken());
        assertThat(reused.status()).isEqualTo(401);
        assertThat(reused.text("code")).isEqualTo("TOKEN_BLOCKED");
        assertThat(post("/auth/refresh",
                Map.of("refreshToken", loggedIn.text("refreshToken"))).status()).isEqualTo(401);
        assertThat(post("/auth/login",
                Map.of("email", user.email(), "password", user.password())).status()).isEqualTo(401);
        // 프로필 요약과 사진 조회는 탈퇴자를 없는 사용자로 본다
        Response summary = get("/users/" + user.userId(), viewer.accessToken());
        assertThat(summary.status()).isEqualTo(404);
        assertThat(summary.text("code")).isEqualTo("NOT_FOUND");
        assertThat(get("/users/" + user.userId() + "/profile-image", null).status()).isEqualTo(404);
        // 중복 검사는 탈퇴 스냅샷을 보지 않아 그 닉네임을 바로 다른 사람이 쓸 수 있다
        assertThat(post("/users/nickname/availability", Map.of("nickname", user.nickname()))
                .bool("available")).isTrue();
    }

    @Test
    @DisplayName("진행 중인 솔로 러닝이 있어도 탈퇴는 막히지 않는다")
    void accountIsDeletedDuringSoloRoom() {
        // given - 방을 열어 활성 신청이 남아 있다
        TestUser user = signUpAndOnboard();
        assertThat(post("/running-rooms/solo", Map.of(), user.accessToken()).status())
                .isEqualTo(201);

        // when
        Response deleted = delete("/users/me", user.accessToken());

        // then
        assertThat(deleted.status()).isEqualTo(204);
        assertThat(post("/auth/login",
                Map.of("email", user.email(), "password", user.password())).status()).isEqualTo(401);
    }

    @Test
    @DisplayName("러닝 중에 탈퇴해도 받은 좌표까지로 러닝을 정리하고 탈퇴된다")
    void accountIsDeletedWhileRunning() {
        // given - 러닝을 시작하고 좌표를 보냈다
        TestUser user = signUpAndOnboard();
        long runningRoomId = post("/running-rooms/solo", Map.of(), user.accessToken())
                .number("runningRoomId");
        try (RunningWebSocket socket = connectRunningWebSocket(user.accessToken())) {
            socket.send("RUNNING_START", Map.of("runningRoomId", runningRoomId));
            socket.await("RUNNING_STARTED");
            socket.send("RUNNING_LOCATION_UPDATE", Map.of("locations", List.of(point(0), point(1))));
            // 좌표에는 ack가 없다 — 같은 연결은 순서대로 처리되니 헬스체크 응답으로 처리 완료를 기다린다
            socket.send("HEALTH_CHECK", Map.of());
            socket.await("HEALTH_CHECKED");
        }
        // 연결을 끊어도 러닝은 끝나지 않는다 — 탈퇴가 연결을 닫으므로 먼저 닫아 둔다

        // when - 종료 처리가 탈퇴보다 먼저 돈다
        Response deleted = delete("/users/me", user.accessToken());

        // then
        assertThat(deleted.status()).isEqualTo(204);
        assertThat(post("/auth/login",
                Map.of("email", user.email(), "password", user.password())).status()).isEqualTo(401);
    }

    private static Map<String, Object> point(int sequence) {
        Map<String, Object> point = new HashMap<>();
        point.put("sequence", sequence);
        point.put("latitude", 37.5665 + sequence * 0.0001);
        point.put("longitude", 126.9780);
        point.put("accuracyMeters", 5.0);
        point.put("cadenceSpm", 170);
        point.put("recordedAt", LocalDateTime.now(APP_ZONE).minusSeconds(10 - sequence)
                .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        return point;
    }
}
