package com.runiverse.e2e.device;

import com.runiverse.e2e.E2eTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 등록 API는 본문 없이 204만 돌려줘 켜진 기기는 DB에서 확인한다.
 * 플랫폼·앱 버전은 기본 헤더(ANDROID·1.2.0)에서 들어간다.
 */
@DisplayName("배포 이미지 대상 푸시 기기 등록 E2E 테스트")
class DeviceE2eTest extends E2eTestSupport {

    private static final String PATH = "/users/me/devices";
    // 동시 요청이 실제로 겹칠 기회를 늘리려고 여러 번 반복한다
    private static final int CONCURRENT_ROUNDS = 5;

    @Test
    @DisplayName("유저당 마지막에 등록한 기기 하나만 켜지고, 계정을 바꾸면 주인이 바뀌며, 로그아웃하면 모두 꺼진다")
    void lastRegisteredDeviceIsTheOnlyActiveOne() {
        // given
        TestUser user = signUpAndOnboard();
        TestUser other = signUpAndOnboard();
        String firstDevice = uniqueDeviceId();
        String secondDevice = uniqueDeviceId();

        // when & then - 처음 등록하면 헤더의 플랫폼·앱 버전과 함께 켜진다
        assertThat(register(firstDevice, "token-1", user.accessToken())).isEqualTo(204);
        assertActiveDevices(user.userId(), firstDevice);
        assertInDb(String.format("""
                EXISTS (SELECT 1 FROM user_devices
                        WHERE device_id = '%s' AND push_token = 'token-1'
                          AND platform = 'ANDROID' AND app_version = '1.2.0')""", firstDevice),
                "헤더의 플랫폼·앱 버전이 저장되지 않았다");

        // when & then - 같은 요청을 다시 보내도 행이 늘지 않는다
        assertThat(register(firstDevice, "token-1", user.accessToken())).isEqualTo(204);
        assertInDb(String.format("(SELECT count(*) FROM user_devices WHERE user_id = '%s') = 1", user.userId()),
                "같은 기기의 행이 늘었다");

        // when & then - 둘째 기기를 등록하면 첫 기기는 꺼진다
        assertThat(register(secondDevice, "token-2", user.accessToken())).isEqualTo(204);
        assertActiveDevices(user.userId(), secondDevice);

        // when & then - 다른 계정이 같은 기기로 등록하면 행 주인이 바뀐다
        assertThat(register(secondDevice, "token-2", other.accessToken())).isEqualTo(204);
        assertActiveDevices(user.userId());
        assertActiveDevices(other.userId(), secondDevice);

        // when & then - 로그아웃하면 그 유저의 기기가 전부 꺼진다
        assertThat(post("/auth/logout", Map.of(), other.accessToken()).status()).isEqualTo(204);
        assertActiveDevices(other.userId());
    }

    @Test
    @DisplayName("한 유저의 두 기기가 동시에 등록해도 500 없이 하나만 켜진다")
    void concurrentRegistrationsLeaveOneActive() {
        for (int i = 0; i < CONCURRENT_ROUNDS; i++) {
            // given - 이미 켜진 기기가 있다
            TestUser user = signUpAndOnboard();
            String oldDevice = uniqueDeviceId();
            register(oldDevice, "token-old", user.accessToken());
            CountDownLatch start = new CountDownLatch(1);

            // when - 줄을 세우지 않으면 서로 상대가 아직 꺼지지 않은 걸 못 봐 둘 다 켜진다
            CompletableFuture<Integer> first = CompletableFuture.supplyAsync(() -> {
                await(start);
                return register(uniqueDeviceId(), "token-1", user.accessToken());
            });
            CompletableFuture<Integer> second = CompletableFuture.supplyAsync(() -> {
                await(start);
                return register(uniqueDeviceId(), "token-2", user.accessToken());
            });
            start.countDown();

            // then
            assertThat(List.of(first.join(), second.join())).containsExactly(204, 204);
            assertInDb(String.format("""
                    (SELECT count(*) FROM user_devices WHERE user_id = '%s' AND is_active) = 1
                    AND NOT EXISTS (SELECT 1 FROM user_devices WHERE device_id = '%s' AND is_active)""",
                            user.userId(), oldDevice),
                    "동시 등록 뒤 켜진 기기가 하나가 아니다");
        }
    }

    @Test
    @DisplayName("다시 로그인하면 그 유저의 기기가 전부 꺼지고, 비밀번호가 틀리면 그대로다")
    void loginDeactivatesDevices() {
        // given - 예전 폰에 등록해 둔 기기가 켜져 있다
        TestUser user = signUpAndOnboard();
        String oldDevice = uniqueDeviceId();
        assertThat(register(oldDevice, "token-1", user.accessToken())).isEqualTo(204);

        // when & then - 비밀번호가 틀리면 기기를 끄지 않는다
        assertThat(post("/auth/login", Map.of("email", user.email(), "password", "WrongPassword1!")).status())
                .isEqualTo(401);
        assertActiveDevices(user.userId(), oldDevice);

        // when & then - 새 폰에서 로그인하면 등록 전이라도 예전 폰이 꺼진다
        assertThat(post("/auth/login", Map.of("email", user.email(), "password", user.password())).status())
                .isEqualTo(200);
        assertActiveDevices(user.userId());
    }

    @Test
    @DisplayName("탈퇴하면 꺼진 기기까지 그 유저의 기기 행이 지워진다")
    void accountDeletionRemovesDevices() {
        // given - 하나는 꺼지고 하나는 켜진 상태다
        TestUser user = signUpAndOnboard();
        register(uniqueDeviceId(), "token-1", user.accessToken());
        register(uniqueDeviceId(), "token-2", user.accessToken());

        // when
        int status = delete("/users/me", user.accessToken()).status();

        // then
        assertThat(status).isEqualTo(204);
        assertInDb(String.format("NOT EXISTS (SELECT 1 FROM user_devices WHERE user_id = '%s')", user.userId()),
                "탈퇴한 유저의 기기 행이 남았다");
    }

    @Test
    @DisplayName("기기 ID가 비었으면 400이고 저장하지 않는다")
    void blankDeviceIdIs400() {
        // given
        TestUser user = signUpAndOnboard();

        // when
        Response response = post(PATH, Map.of("deviceId", " ", "pushToken", "token-1"), user.accessToken());

        // then
        assertThat(response.status()).isEqualTo(400);
        assertThat(response.text("code")).isEqualTo("INVALID_REQUEST");
        assertThat(response.text("message")).isEqualTo("입력값이 올바르지 않습니다.");
        assertInDb(String.format("NOT EXISTS (SELECT 1 FROM user_devices WHERE user_id = '%s')", user.userId()),
                "거절한 요청이 저장됐다");
    }

    private int register(String deviceId, String pushToken, String accessToken) {
        return post(PATH, Map.of("deviceId", deviceId, "pushToken", pushToken), accessToken).status();
    }

    private static String uniqueDeviceId() {
        return UUID.randomUUID().toString();
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    // 켜진 기기가 정확히 이것들뿐인지 본다 — 비워 두면 켜진 기기가 없어야 한다
    private void assertActiveDevices(String userId, String... deviceIds) {
        String expected = Arrays.stream(deviceIds).sorted()
                .map(deviceId -> "'" + deviceId + "'")
                .collect(Collectors.joining(", "));
        assertInDb(String.format("""
                (SELECT coalesce(array_agg(device_id ORDER BY device_id), '{}')
                 FROM user_devices WHERE user_id = '%s' AND is_active) = ARRAY[%s]::varchar[]""",
                        userId, expected),
                "켜진 기기가 기대와 다르다");
    }

    // 결과를 읽어 오는 헬퍼가 없어 DB 안에서 판정한다 — 조건이 거짓이면 예외가 나 executeSql이 실패한다
    private void assertInDb(String condition, String failureMessage) {
        executeSql(String.format("""
                DO $$
                BEGIN
                    IF NOT (%s) THEN
                        RAISE EXCEPTION '%s';
                    END IF;
                END $$;
                """, condition, failureMessage));
    }
}
