package com.runiverse.e2e.legacy;

import com.runiverse.e2e.E2eTestSupport;
import com.runiverse.e2e.SseStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 1.2.0 심사 기간에 이미 깔린 1.1.x 앱이 1.2.0 서버를 그대로 쓸 수 있는지 본다 — 경로·형식은 1.1.1 서버(main) 계약이다.
 * 요청은 모두 1.1.1 앱 헤더로 보낸다. 매칭 확정(MATCH_STARTED)은 슬롯 시각이 돼야 일어나 여기서 다루지 않는다(단위 테스트가 본다).
 */
@DisplayName("배포 이미지 대상 1.1.x 호환 E2E 테스트")
class LegacyV11E2eTest extends E2eTestSupport {

    private static final ZoneId APP_ZONE = ZoneId.of("Asia/Seoul");
    private static final Map<String, String> APP_1_1_1 = Map.of("X-App-Platform", "ANDROID", "X-App-Version", "1.1.1");
    private static final Map<String, String> APP_1_2_0 = Map.of("X-App-Platform", "ANDROID", "X-App-Version", "1.2.0");
    // 다른 매칭 E2E(내일·모레)와 한 방에 섞이지 않게 사흘 뒤를 쓴다
    private static final LocalDate SLOT_DATE = LocalDate.now(APP_ZONE).plusDays(3);
    private static final LocalDateTime SLOTS_SLOT = SLOT_DATE.atTime(20, 0);
    private static final LocalDateTime FLOW_SLOT = SLOT_DATE.atTime(21, 0);
    private static final int DISTANCE = 5000;

    @Test
    @DisplayName("슬롯 조회는 요청한 거리 하나의 slots만 1.1.1 형식으로 내리고, 거리를 빠뜨리거나 선택지 밖이면 400이다")
    void slotsKeepLegacyShape() {
        // given
        TestUser user = signUpAndOnboard();
        assertThat(legacyApply(user, SLOTS_SLOT).status()).isEqualTo(201);

        // when
        Response slots = get("/running-matches/slots?date=" + SLOT_DATE + "&targetDistanceMeters=" + DISTANCE,
                user.accessToken(), APP_1_1_1);
        Response missing = get("/running-matches/slots?date=" + SLOT_DATE, user.accessToken(), APP_1_1_1);
        Response offDistance = get("/running-matches/slots?date=" + SLOT_DATE + "&targetDistanceMeters=4000",
                user.accessToken(), APP_1_1_1);

        // then - 거리 묶음(distances) 없이 slots 하나다
        assertThat(slots.status()).isEqualTo(200);
        assertThat(slots.body().keySet()).containsExactly("slots");
        List<Map<String, Object>> items = slots.objects("slots");
        assertThat(items).hasSize(5);
        assertThat(items.get(0).keySet()).containsExactlyInAnyOrder("scheduledStartAt", "waitingCount", "selectable");
        assertThat(items).extracting(slot -> slot.get("waitingCount"))
                .containsExactly(0, 0, 1, 0, 0);

        assertThat(missing.status()).isEqualTo(400);
        assertThat(missing.text("message")).isEqualTo("목표 거리는 필수입니다.");
        assertThat(offDistance.status()).isEqualTo(400);
        assertThat(offDistance.text("code")).isEqualTo("INVALID_REQUEST");
        assertThat(offDistance.text("message")).isEqualTo("목표 거리는 3000, 5000, 10000 중 하나여야 합니다.");

        assertThat(delete("/running-matches", user.accessToken(), APP_1_1_1).status()).isEqualTo(204);
    }

    @Test
    @DisplayName("1.1.x 신청·스트림·취소가 1.2.0 사용자와 같은 방에서 이어진다")
    void legacyFlowSharesRoomWithNewApp() {
        // given - 1.1.x 사용자가 옛 경로로 신청한다
        TestUser legacyUser = signUpAndOnboard();
        Response applied = legacyApply(legacyUser, FLOW_SLOT);
        assertThat(applied.status()).isEqualTo(201);
        assertThat(applied.body().keySet()).containsExactly("runningRoomId");
        int runningRoomId = applied.number("runningRoomId");
        assertThat(get("/users/me/status", legacyUser.accessToken(), APP_1_1_1).text("status")).isEqualTo("WAITING");

        try (SseStream stream = openSseStream("/running-matches/stream", legacyUser.accessToken(), APP_1_1_1)) {
            // when - 연결 직후 스냅샷은 1.1.1 형식이다(type·invitations 없음)
            assertThat(stream.status()).isEqualTo(200);
            Map<String, Object> snapshot = stream.await("MATCH_ROOM_UPDATED");
            assertThat(snapshot.keySet()).containsExactlyInAnyOrder(
                    "runningRoomId", "status", "scheduledStartAt", "closeAt",
                    "targetDistanceMeters", "teamAveragePaceSecondsPerKm", "players");
            assertThat(((Number) snapshot.get("runningRoomId")).intValue()).isEqualTo(runningRoomId);
            assertThat(userIdsOf(snapshot)).containsExactly(legacyUser.userId());

            // then - 1.2.0 사용자가 새 경로로 같은 조건을 신청하면 같은 방에 붙고, 1.1.x 스트림에도 갱신이 간다
            TestUser newUser = signUpAndOnboard();
            Response joined = post("/match-rooms", Map.of(
                    "scheduledStartAt", FLOW_SLOT.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                    "targetDistanceMeters", DISTANCE), newUser.accessToken(), APP_1_2_0);
            assertThat(joined.number("runningRoomId")).isEqualTo(runningRoomId);
            Map<String, Object> updated = stream.await("MATCH_ROOM_UPDATED");
            assertThat(updated).doesNotContainKeys("type", "invitations");
            assertThat(userIdsOf(updated)).containsExactlyInAnyOrder(legacyUser.userId(), newUser.userId());

            // 1.2.0 사용자가 나가면 1.1.x 스트림에서도 빠진다
            assertThat(post("/match-rooms/leave", null, newUser.accessToken(), APP_1_2_0).status()).isEqualTo(204);
            assertThat(userIdsOf(stream.await("MATCH_ROOM_UPDATED"))).containsExactly(legacyUser.userId());
        }

        // 1.1.x 취소는 옛 DELETE다 — 나간 뒤에는 붙을 방이 없어 옛 스트림도 404다
        assertThat(delete("/running-matches", legacyUser.accessToken(), APP_1_1_1).status()).isEqualTo(204);
        assertThat(get("/users/me/status", legacyUser.accessToken(), APP_1_1_1).text("status")).isEqualTo("IDLE");
        try (SseStream stream = openSseStream("/running-matches/stream", legacyUser.accessToken(), APP_1_1_1)) {
            assertThat(stream.status()).isEqualTo(404);
        }
        Response cancelAgain = delete("/running-matches", legacyUser.accessToken(), APP_1_1_1);
        assertThat(cancelAgain.status()).isEqualTo(404);
        assertThat(cancelAgain.text("code")).isEqualTo("NOT_FOUND");
    }

    @Test
    @DisplayName("1.1.x도 남의 프로필을 그대로 조회한다 — 예전 friendStatus는 1.1.x가 읽지 않아 싣지 않는다")
    void profileWorksWithoutLegacyFriendStatus() {
        // given
        TestUser viewer = signUpAndOnboard();
        TestUser other = signUpAndOnboard();

        // when
        Response profile = get("/users/" + other.userId(), viewer.accessToken(), APP_1_1_1);

        // then - 1.1.x가 읽는 필드(닉네임·친구 수 등)는 1.1.1 서버와 같은 이름이다
        assertThat(profile.status()).isEqualTo(200);
        assertThat(profile.text("nickname")).isEqualTo(other.nickname());
        assertThat(profile.body()).containsKey("friendCount").doesNotContainKey("friendStatus");
    }

    @Test
    @DisplayName("공개 뒤 최소 버전을 1.2.0으로 올리면 1.1.x의 옛 경로는 403, 1.2.0은 그대로다")
    void minimumBumpBlocksLegacyApp() {
        // given
        TestUser user = signUpAndOnboard();
        String slotsPath = "/running-matches/slots?date=" + SLOT_DATE + "&targetDistanceMeters=" + DISTANCE;
        assertThat(get(slotsPath, user.accessToken(), APP_1_1_1).status()).isEqualTo(200);

        // when - 운영이 할 일과 같은 SQL이다. 캐시 없이 매 요청 읽어 다음 요청부터 걸린다
        executeSql("UPDATE app_versions SET minimum_version = '1.2.0' WHERE platform = 'ANDROID';");
        try {
            // then
            Response blocked = get(slotsPath, user.accessToken(), APP_1_1_1);
            assertThat(blocked.status()).isEqualTo(403);
            assertThat(blocked.text("code")).isEqualTo("APP_UPDATE_REQUIRED");
            assertThat(get("/match-rooms/slots?date=" + SLOT_DATE, user.accessToken(), APP_1_2_0).status())
                    .isEqualTo(200);
        } finally {
            // 다른 테스트가 1.1.0 헤더로 통과를 확인한다(sql/app-versions.sql) — 시드 값으로 되돌린다
            executeSql("UPDATE app_versions SET minimum_version = '1.1.0' WHERE platform = 'ANDROID';");
        }
    }

    private Response legacyApply(TestUser user, LocalDateTime slot) {
        return post("/running-matches", Map.of(
                "scheduledStartAt", slot.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                "targetDistanceMeters", DISTANCE), user.accessToken(), APP_1_1_1);
    }

    @SuppressWarnings("unchecked")
    private static List<String> userIdsOf(Map<String, Object> room) {
        return ((List<Map<String, Object>>) room.get("players")).stream()
                .map(player -> (String) player.get("userId"))
                .toList();
    }
}
