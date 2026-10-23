package com.runiverse.e2e.app;

import com.runiverse.e2e.E2eTestSupport;
import com.runiverse.e2e.RunningWebSocket;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 최소·최신 버전은 run-e2e.sh가 sql/app-versions.sql로 넣는다 —
 * iOS 최소 1.0.0·최신 1.1.0, 안드로이드 최소 1.1.0·최신 1.2.0.
 */
@DisplayName("배포 이미지 대상 앱 버전 확인·구버전 거절 E2E 테스트")
class AppVersionE2eTest extends E2eTestSupport {

    @Test
    @DisplayName("플랫폼마다 따로 둔 최소·최신 버전을 내린다")
    void returnsVersionsPerPlatform() {
        // when - 실제 DB에서 플랫폼으로 조회한다
        Response android = get("/app/version?platform=android", null);
        Response ios = get("/app/version?platform=IOS", null);

        // then
        assertThat(android.status()).isEqualTo(200);
        assertThat(android.text("minimumVersion")).isEqualTo("1.1.0");
        assertThat(android.text("latestVersion")).isEqualTo("1.2.0");
        assertThat(ios.status()).isEqualTo(200);
        assertThat(ios.text("minimumVersion")).isEqualTo("1.0.0");
        assertThat(ios.text("latestVersion")).isEqualTo("1.1.0");
    }

    @Test
    @DisplayName("플랫폼이 없으면 400 필수 문구다")
    void missingPlatformIs400() {
        // when
        Response response = get("/app/version", null);

        // then
        assertThat(response.status()).isEqualTo(400);
        assertThat(response.text("code")).isEqualTo("INVALID_REQUEST");
        assertThat(response.text("message")).isEqualTo("플랫폼은 필수입니다.");
    }

    @Test
    @DisplayName("구버전 앱도 버전 확인 API는 답을 받는다")
    void oldAppStillGetsVersions() {
        // when - 이 API까지 막으면 앱이 업데이트가 필요하다는 것도 알 수 없다
        Response response = get("/app/version?platform=ANDROID", null, appVersion("ANDROID", "1.0.0"));

        // then
        assertThat(response.status()).isEqualTo(200);
        assertThat(response.text("minimumVersion")).isEqualTo("1.1.0");
    }

    @Test
    @DisplayName("버전 확인만 공개되고, /app 아래 다른 경로는 토큰이 필요하다")
    void onlyVersionCheckIsPublic() {
        // when - 공개 경로가 /app/**로 넓어지면 토큰 없이도 통과해 401이 아니게 된다
        Response notices = get("/app/notices", null);
        Response nested = get("/app/version/latest", null);

        // then
        assertThat(notices.status()).isEqualTo(401);
        assertThat(nested.status()).isEqualTo(401);
    }

    @Test
    @DisplayName("최소 버전보다 낮으면 토큰을 보기 전에 403으로 거절한다")
    void rejectsOldAppBeforeTokenCheck() {
        // when - 토큰 검사가 먼저면 401이 나간다
        Response response = get("/users/me", "not-a-token", appVersion("ANDROID", "1.0.9"));

        // then
        assertThat(response.status()).isEqualTo(403);
        assertThat(response.text("code")).isEqualTo("APP_UPDATE_REQUIRED");
        assertThat(response.text("message")).isEqualTo("앱 업데이트가 필요합니다.");
    }

    @Test
    @DisplayName("앱 버전 헤더가 없으면 토큰을 보기 전에 403으로 거절한다")
    void rejectsAppWithoutHeaders() {
        // when - 헤더를 싣지 않는 구버전 앱(안드로이드 1.0.0)이다
        Response response = get("/users/me", "not-a-token", Map.of());

        // then
        assertThat(response.status()).isEqualTo(403);
        assertThat(response.text("code")).isEqualTo("APP_UPDATE_REQUIRED");
    }

    @Test
    @DisplayName("앱 버전 헤더 형식이 틀리면 403으로 거절한다")
    void rejectsMalformedHeaders() {
        // when - 통과시키면 아무 값이나 실어 검사를 건너뛸 수 있다
        Response response = get("/users/me", "not-a-token", appVersion("ANDROID", "1.1"));

        // then
        assertThat(response.status()).isEqualTo(403);
        assertThat(response.text("code")).isEqualTo("APP_UPDATE_REQUIRED");
    }

    @Test
    @DisplayName("앱 버전 헤더가 없어도 버전 확인 API는 답을 받는다")
    void versionCheckWithoutHeaders() {
        // when - 헤더를 싣지 않는 앱도 업데이트가 필요하다는 것을 알아야 한다
        Response response = get("/app/version?platform=ANDROID", null, Map.of());

        // then
        assertThat(response.status()).isEqualTo(200);
    }

    @Test
    @DisplayName("최소 버전 이상이면 그대로 처리하고 플랫폼마다 따로 판정한다")
    void passesSupportedApp() {
        // given
        String accessToken = signUp(uniqueEmail(), "Password123!");

        // when - 같은 1.0.0이라도 iOS는 최소 버전이고 안드로이드는 최소 버전 미만이다
        Response android = get("/users/me", accessToken, appVersion("ANDROID", "1.1.0"));
        Response ios = get("/users/me", accessToken, appVersion("IOS", "1.0.0"));
        Response oldAndroid = get("/users/me", accessToken, appVersion("ANDROID", "1.0.0"));

        // then
        assertThat(android.status()).isEqualTo(200);
        assertThat(ios.status()).isEqualTo(200);
        assertThat(oldAndroid.status()).isEqualTo(403);
    }

    @Test
    @DisplayName("구버전 앱은 러닝 WebSocket에도 연결하지 못한다")
    void rejectsOldAppHandshake() {
        // when - then - 핸드셰이크도 HTTP 요청이라 같은 검사를 지난다. 토큰보다 먼저라 401이 아니다
        assertThatThrownBy(() -> connectRunningWebSocket(null, appVersion("ANDROID", "1.0.9")))
                .isInstanceOf(RunningWebSocket.HandshakeFailedException.class)
                .hasMessageContaining("403");
    }

    private static Map<String, String> appVersion(String platform, String versionName) {
        return Map.of("X-App-Platform", platform, "X-App-Version", versionName);
    }
}
