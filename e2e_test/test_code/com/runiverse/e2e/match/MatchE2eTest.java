package com.runiverse.e2e.match;

import com.runiverse.e2e.E2eTestSupport;
import com.runiverse.e2e.SseStream;
import com.runiverse.e2e.RunningWebSocket;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("배포 이미지 대상 매칭 E2E 테스트")
class MatchE2eTest extends E2eTestSupport {

    private static final ZoneId APP_ZONE = ZoneId.of("Asia/Seoul");   // 호스트 시간대로 만들면 슬롯이 어긋난다
    private static final int CLOSE_OFFSET_MINUTES = 10;   // match.close-offset
    private static final int ONBOARDING_PACE = 330;       // signUpAndOnboard의 평균 페이스

    // 같은 슬롯·거리면 테스트끼리 한 방에 합쳐져 테스트마다 다른 내일 슬롯을 쓴다
    private static final LocalDate TOMORROW = LocalDate.now(APP_ZONE).plusDays(1);
    private static final LocalDateTime FLOW_SLOT = TOMORROW.atTime(18, 0);
    private static final LocalDateTime GUARD_SLOT = TOMORROW.atTime(18, 30);
    private static final LocalDateTime DELETION_SLOT = TOMORROW.atTime(19, 0);
    private static final int DISTANCE = 3000;

    @Test
    @DisplayName("신청부터 스트림 갱신·취소까지 실제 컨테이너 위에서 한 흐름으로 이어진다")
    void fullFlow() {
        // given - 1. 첫 신청자가 1인 방을 열고 대기 상태가 된다
        TestUser host = signUpAndOnboard();
        Response applied = apply(host, FLOW_SLOT, DISTANCE);
        assertThat(applied.status()).isEqualTo(201);
        int runningRoomId = applied.number("runningRoomId");

        Response waiting = get("/users/me/status", host.accessToken());
        assertThat(waiting.status()).isEqualTo(200);
        assertThat(waiting.text("status")).isEqualTo("WAITING");
        assertThat(waiting.text("type")).isEqualTo("MATCH");
        assertThat(waiting.number("runningRoomId")).isEqualTo(runningRoomId);
        assertThat(LocalDateTime.parse(waiting.text("scheduledStartAt"))).isEqualTo(FLOW_SLOT);
        assertThat(waiting.number("targetDistanceMeters")).isEqualTo(DISTANCE);
        assertThat(waiting.text("cooldownUntil")).isNull();

        try (SseStream stream = openMatchStream(host.accessToken())) {
            // when - 2. 연결 직후 스냅샷이 방 정보를 나른다
            assertThat(stream.status()).isEqualTo(200);
            Map<String, Object> snapshot = stream.await("MATCH_ROOM_UPDATED");

            // then
            assertThat(((Number) snapshot.get("runningRoomId")).intValue()).isEqualTo(runningRoomId);
            assertThat(snapshot.get("status")).isEqualTo("MATCHING");
            assertThat(LocalDateTime.parse((String) snapshot.get("scheduledStartAt"))).isEqualTo(FLOW_SLOT);
            assertThat(LocalDateTime.parse((String) snapshot.get("closeAt")))
                    .isEqualTo(FLOW_SLOT.minusMinutes(CLOSE_OFFSET_MINUTES));
            assertThat(snapshot.get("targetDistanceMeters")).isEqualTo(DISTANCE);
            assertThat(snapshot.get("teamAveragePaceSecondsPerKm")).isEqualTo(ONBOARDING_PACE);
            assertThat(userIdsOf(snapshot)).containsExactly(host.userId());

            // 3. 같은 조건의 두 번째 신청자는 같은 방에 붙고, 기존 참가자에게 갱신이 간다
            TestUser guest = signUpAndOnboard();
            Response joined = apply(guest, FLOW_SLOT, DISTANCE);
            assertThat(joined.status()).isEqualTo(201);
            assertThat(joined.number("runningRoomId")).isEqualTo(runningRoomId);
            assertThat(userIdsOf(stream.await("MATCH_ROOM_UPDATED")))
                    .containsExactlyInAnyOrder(host.userId(), guest.userId());

            // 4. 마감 전 취소는 대기 취소다 — 남은 참가자에게 줄어든 인원이 간다
            assertThat(post("/match-rooms/leave", null, guest.accessToken()).status()).isEqualTo(204);
            assertThat(userIdsOf(stream.await("MATCH_ROOM_UPDATED")))
                    .containsExactly(host.userId());
            assertThat(get("/users/me/status", guest.accessToken()).text("status")).isEqualTo("IDLE");
            assertThat(get("/users/me/status", guest.accessToken()).text("cooldownUntil")).isNull();
        }

        // 5. 마지막 참가자가 나가면 활성 신청이 없어 IDLE이다
        assertThat(post("/match-rooms/leave", null, host.accessToken()).status()).isEqualTo(204);
        Response idle = get("/users/me/status", host.accessToken());
        assertThat(idle.text("status")).isEqualTo("IDLE");
        assertThat(idle.number("runningRoomId")).isNull();
    }

    @Test
    @DisplayName("활성 신청은 하나뿐이고, 신청이 없으면 취소와 스트림 연결이 404다")
    void activeApplicationIsGuarded() {
        // given
        TestUser user = signUpAndOnboard();

        // when - then - 신청 전에는 붙을 방이 없어 스트림이 본문 없이 거절된다
        try (SseStream stream = openMatchStream(user.accessToken())) {
            assertThat(stream.status()).isEqualTo(404);
        }
        Response cancelWithout = post("/match-rooms/leave", null, user.accessToken());
        assertThat(cancelWithout.status()).isEqualTo(404);
        assertThat(cancelWithout.text("code")).isEqualTo("NOT_FOUND");

        assertThat(apply(user, GUARD_SLOT, DISTANCE).status()).isEqualTo(201);
        Response duplicated = apply(user, GUARD_SLOT.plusMinutes(30), DISTANCE);
        assertThat(duplicated.status()).isEqualTo(409);
        assertThat(duplicated.text("code")).isEqualTo("MATCH_ALREADY_IN_PROGRESS");

        assertThat(post("/match-rooms/leave", null, user.accessToken()).status()).isEqualTo(204);
        assertThat(apply(user, GUARD_SLOT, DISTANCE).status()).isEqualTo(201);
        assertThat(post("/match-rooms/leave", null, user.accessToken()).status()).isEqualTo(204);
    }

    @Test
    @DisplayName("솔로 러닝이 활성이면 매칭을 신청할 수 없다")
    void soloRunningBlocksMatch() {
        // given - 솔로 방도 활성 신청이다
        TestUser user = signUpAndOnboard();
        assertThat(post("/running-rooms/solo", Map.of(), user.accessToken()).status()).isEqualTo(201);

        // when
        Response response = apply(user, GUARD_SLOT, DISTANCE);

        // then
        assertThat(response.status()).isEqualTo(409);
        assertThat(response.text("code")).isEqualTo("MATCH_ALREADY_IN_PROGRESS");
    }

    @Test
    @DisplayName("온보딩을 마치지 않으면 매칭 조건에 쓸 페이스가 없어 409로 막힌다")
    void onboardingIsRequired() {
        // given - 가입만 하고 온보딩은 건너뛴다
        String accessToken = signUp(uniqueEmail(), "Password123!");

        // when
        Response response = post("/match-rooms", Map.of(
                "scheduledStartAt", GUARD_SLOT.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                "targetDistanceMeters", DISTANCE
        ), accessToken);

        // then
        assertThat(response.status()).isEqualTo(409);
        assertThat(response.text("code")).isEqualTo("ONBOARDING_NOT_COMPLETED");
    }

    @Test
    @DisplayName("러닝이 시작된 뒤에는 취소 API로 끊을 수 없어 409로 막힌다")
    void cancelIsRejectedAfterRunningStarted() {
        // given - 취소는 신청이 RUNNING인지로만 막아 바로 시작되는 솔로 방으로 만든다
        TestUser user = signUpAndOnboard();
        long runningRoomId = post("/running-rooms/solo", Map.of(), user.accessToken())
                .number("runningRoomId");
        try (RunningWebSocket socket = connectRunningWebSocket(user.accessToken())) {
            socket.send("RUNNING_START", Map.of("runningRoomId", runningRoomId));
            socket.await("RUNNING_STARTED");

            // when
            Response response = post("/match-rooms/leave", null, user.accessToken());

            // then - 종료는 WS RUNNING_FINISH가 맡는다
            assertThat(response.status()).isEqualTo(409);
            assertThat(response.text("code")).isEqualTo("MATCH_ALREADY_STARTED");
            assertThat(get("/users/me/status", user.accessToken()).text("status"))
                    .isEqualTo("RUNNING");
        }
    }

    @Test
    @DisplayName("모집이 마감된 슬롯은 409, 선택지 밖의 값은 400으로 걸린다")
    void invalidSlotIsRejected() {
        // given
        TestUser user = signUpAndOnboard();
        LocalDateTime yesterday = LocalDate.now(APP_ZONE).minusDays(1).atTime(18, 0);

        // when
        Response closed = apply(user, yesterday, DISTANCE);
        Response offSlot = apply(user, TOMORROW.atTime(21, 15), DISTANCE);
        Response offDistance = apply(user, TOMORROW.atTime(21, 30), 4000);

        // then - 마감은 운영값·현재 시각에 걸린 정책이라 유스케이스가 409로 막는다
        assertThat(closed.status()).isEqualTo(409);
        assertThat(closed.text("code")).isEqualTo("MATCH_SLOT_CLOSED");
        assertThat(offSlot.status()).isEqualTo(400);
        assertThat(offSlot.text("code")).isEqualTo("INVALID_REQUEST");
        // 선택지 검증이 DTO에서 유스케이스로 옮겨 가도 문구는 명세 그대로다(설정값으로 만든다)
        assertThat(offSlot.text("message")).isEqualTo("시작 시각은 18:00부터 22:00까지 30분 간격으로만 선택할 수 있습니다.");
        assertThat(offDistance.status()).isEqualTo(400);
        assertThat(offDistance.text("code")).isEqualTo("INVALID_REQUEST");
        assertThat(get("/users/me/status", user.accessToken()).text("status")).isEqualTo("IDLE");
    }

    @Test
    @DisplayName("슬롯 조회는 그날 모집 중인 대기 인원을 거리·슬롯별로 세고, 잘못된 날짜는 400으로 걸린다")
    void slotsCountWaitingPlayers() {
        // given - 다른 테스트의 내일 슬롯과 섞이지 않게 모레를 쓴다. 모레는 이 테스트만 신청한다
        LocalDate slotDate = TOMORROW.plusDays(1);
        TestUser first = signUpAndOnboard();
        TestUser second = signUpAndOnboard();
        TestUser third = signUpAndOnboard();
        assertThat(apply(first, slotDate.atTime(20, 0), 10000).status()).isEqualTo(201);
        assertThat(apply(second, slotDate.atTime(20, 0), 10000).status()).isEqualTo(201);
        assertThat(apply(third, slotDate.atTime(20, 0), 5000).status()).isEqualTo(201);

        // when
        Response slots = get("/match-rooms/slots?date=" + slotDate, first.accessToken());
        Response malformed = get("/match-rooms/slots?date=2026/07/25", first.accessToken());
        Response pastDate = get("/match-rooms/slots?date=" + LocalDate.now(APP_ZONE).minusDays(1),
                first.accessToken());

        // then - 거리 오름차순, 거리마다 9개 슬롯이 오름차순이고 같은 시각이어도 거리끼리 합산하지 않는다
        //        (JPQL 집계가 실제 DB에서 돈다)
        assertThat(slots.status()).isEqualTo(200);
        assertThat(slots.body().keySet()).containsExactly("distances");
        List<Map<String, Object>> distances = slots.objects("distances");
        assertThat(distances).extracting(distance -> distance.get("targetDistanceMeters"))
                .containsExactly(3000, 5000, 10000);
        assertThat(slotsOf(distances, 3000)).extracting(slot -> LocalDateTime.parse((String) slot.get("scheduledStartAt")))
                .containsExactly(
                        slotDate.atTime(18, 0), slotDate.atTime(18, 30), slotDate.atTime(19, 0),
                        slotDate.atTime(19, 30), slotDate.atTime(20, 0), slotDate.atTime(20, 30),
                        slotDate.atTime(21, 0), slotDate.atTime(21, 30), slotDate.atTime(22, 0));
        assertThat(slotsOf(distances, 3000)).extracting(slot -> slot.get("waitingCount"))
                .containsExactly(0, 0, 0, 0, 0, 0, 0, 0, 0);
        assertThat(slotsOf(distances, 5000)).extracting(slot -> slot.get("waitingCount"))
                .containsExactly(0, 0, 0, 0, 1, 0, 0, 0, 0);
        assertThat(slotsOf(distances, 10000)).extracting(slot -> slot.get("waitingCount"))
                .containsExactly(0, 0, 0, 0, 2, 0, 0, 0, 0);
        for (int distanceMeters : List.of(3000, 5000, 10000)) {
            assertThat(slotsOf(distances, distanceMeters)).extracting(slot -> slot.get("selectable")).containsOnly(true);
        }

        assertThat(malformed.status()).isEqualTo(400);
        assertThat(malformed.text("code")).isEqualTo("INVALID_REQUEST");
        assertThat(malformed.text("message")).isEqualTo("입력값이 올바르지 않습니다.");
        assertThat(pastDate.status()).isEqualTo(400);
        assertThat(pastDate.text("message")).isEqualTo("조회 날짜는 오늘 이전일 수 없습니다.");
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> slotsOf(List<Map<String, Object>> distances, int distanceMeters) {
        return distances.stream()
                .filter(distance -> distance.get("targetDistanceMeters").equals(distanceMeters))
                .findFirst()
                .map(distance -> (List<Map<String, Object>>) distance.get("slots"))
                .orElseThrow();
    }

    @Test
    @DisplayName("대기 중에 탈퇴하면 같은 방 참가자의 스트림에서 빠진다")
    void deletedUserLeavesWaitingRoom() {
        // given
        TestUser host = signUpAndOnboard();
        TestUser leaver = signUpAndOnboard();
        int runningRoomId = apply(host, DELETION_SLOT, DISTANCE).number("runningRoomId");
        assertThat(apply(leaver, DELETION_SLOT, DISTANCE).number("runningRoomId"))
                .isEqualTo(runningRoomId);

        try (SseStream stream = openMatchStream(host.accessToken())) {
            assertThat(userIdsOf(stream.await("MATCH_ROOM_UPDATED")))
                    .containsExactlyInAnyOrder(host.userId(), leaver.userId());

            // when - 탈퇴는 활성 신청 때문에 막히지 않고, 대기 취소를 먼저 적용한다
            assertThat(delete("/users/me", leaver.accessToken()).status()).isEqualTo(204);

            // then
            assertThat(userIdsOf(stream.await("MATCH_ROOM_UPDATED")))
                    .containsExactly(host.userId());
        }
        assertThat(get("/users/me/status", host.accessToken()).text("status")).isEqualTo("WAITING");
        assertThat(post("/match-rooms/leave", null, host.accessToken()).status()).isEqualTo(204);
    }

    private Response apply(TestUser user, LocalDateTime scheduledStartAt, int targetDistanceMeters) {
        return post("/match-rooms", Map.of(
                "scheduledStartAt", scheduledStartAt.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                "targetDistanceMeters", targetDistanceMeters
        ), user.accessToken());
    }

    @SuppressWarnings("unchecked")
    private static List<Object> userIdsOf(Map<String, Object> roomInfo) {
        return ((List<Map<String, Object>>) roomInfo.get("players")).stream()
                .map(player -> player.get("userId"))
                .toList();
    }
}
