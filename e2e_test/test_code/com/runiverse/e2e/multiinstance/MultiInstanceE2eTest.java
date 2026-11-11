package com.runiverse.e2e.multiinstance;

import com.runiverse.e2e.E2eTestSupport;
import com.runiverse.e2e.RunningWebSocket;
import com.runiverse.e2e.SseStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

// ALB 뒤 다중 인스턴스를 흉내 낸다 — 같은 DB·Redis에 붙은 앱 두 대에 테스트가 직접 골라 붙는다.
// 기본 E2E 스택은 앱 한 대라 두 번째 인스턴스(E2E_SECOND_BASE_URL)를 띄운 경우에만 돈다.
// 기대값은 "올바른 동작"이다 — 실패하면 그 경우가 아직 다중 인스턴스에서 지켜지지 않는다는 뜻이다
// (architecture.md 「다중 인스턴스」의 세 경우)
@EnabledIfEnvironmentVariable(named = "E2E_SECOND_BASE_URL", matches = ".+")
@DisplayName("다중 인스턴스(로드 밸런서) E2E 테스트")
class MultiInstanceE2eTest extends E2eTestSupport {

    private static final ZoneId APP_ZONE = ZoneId.of("Asia/Seoul");
    private static final LocalDate TOMORROW = LocalDate.now(APP_ZONE).plusDays(1);
    // 오지 않아야 할 이벤트를 기다리는 시간 — Redis 왕복은 수십 ms라 넉넉하다
    private static final Duration QUIET = Duration.ofSeconds(3);

    private static String one() {
        return primaryBaseUrl();
    }

    private static String two() {
        return secondBaseUrl();
    }

    private Response apply(String baseUrl, TestUser user, LocalDateTime slot, int distance) {
        Response response = postOn(baseUrl, "/match-rooms", Map.of(
                "scheduledStartAt", slot.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                "targetDistanceMeters", distance), user.accessToken());
        assertThat(response.status()).as("매칭 신청 %s", response.body()).isEqualTo(201);
        return response;
    }

    private void leave(String baseUrl, TestUser user) {
        postOn(baseUrl, "/match-rooms/leave", null, user.accessToken());
    }

    private Response chat(String baseUrl, TestUser user, int roomId, String content) {
        Response response = postOn(baseUrl, "/match-rooms/" + roomId + "/chats", Map.of("content", content),
                user.accessToken());
        assertThat(response.status()).as("채팅 보내기 %s", response.body()).isEqualTo(201);
        return response;
    }

    private static int roomOf(Map<String, Object> event) {
        Object data = event.get("data");
        if (data instanceof Map<?, ?> map && map.get("runningRoomId") instanceof Number number) {
            return number.intValue();
        }
        return -1;
    }

    private static List<String> namesOf(List<Map<String, Object>> events) {
        return events.stream().map(event -> (String) event.get("event")).toList();
    }

    @Nested
    @DisplayName("매칭 스트림")
    class MatchStreamTest {

        @Test
        @DisplayName("[같은 인스턴스] 옛 연결을 연 채 새 방으로 갈아타면 옛 방 채팅·인원 변동이 새 연결로 가지 않는다")
        void sameInstanceRoomSwitchDoesNotLeak() {
            TestUser chulsu = signUpAndOnboard();
            TestUser younghee = signUpAndOnboard();
            TestUser minji = signUpAndOnboard();
            int oldRoom = apply(one(), chulsu, TOMORROW.atTime(18, 0), 3000).number("runningRoomId");
            apply(one(), younghee, TOMORROW.atTime(18, 0), 3000);
            apply(one(), minji, TOMORROW.atTime(18, 0), 3000);

            try (SseStream oldStream = openMatchStreamOn(one(), chulsu.accessToken());
                 SseStream minjiStream = openMatchStreamOn(one(), minji.accessToken())) {
                oldStream.await("MATCH_ROOM_ENTRANCE");
                minjiStream.await("MATCH_ROOM_ENTRANCE");

                // 철수가 나가기 뒤 앱이 옛 스트림을 닫기 전에 새 방 스트림을 같은 인스턴스에 연다
                leave(one(), chulsu);
                int newRoom = apply(one(), chulsu, TOMORROW.atTime(19, 0), 3000).number("runningRoomId");
                try (SseStream newStream = openMatchStreamOn(one(), chulsu.accessToken())) {
                    assertThat(newStream.await("MATCH_ROOM_ENTRANCE").get("runningRoomId")).isEqualTo(newRoom);
                    // 같은 인스턴스라 서버가 옛 스트림을 바로 닫는다
                    assertThat(namesOf(oldStream.drainFor(QUIET))).contains(SseStream.ENDED);

                    // 옛 방에서 영희가 말하고(다른 인스턴스로 보냄), 민지가 나가 인원이 바뀐다
                    chat(two(), younghee, oldRoom, "옛 방에서 보낸 말");
                    assertThat(minjiStream.await("ROOM_CHAT_SENT").get("runningRoomId")).isEqualTo(oldRoom);
                    leave(one(), minji);

                    // then - 새 연결로는 옛 방 이벤트가 하나도 오면 안 된다
                    List<Map<String, Object>> leaked = newStream.drainFor(QUIET).stream()
                            .filter(event -> roomOf(event) == oldRoom).toList();
                    assertThat(namesOf(leaked)).as("새 연결로 새어 들어온 옛 방 이벤트").isEmpty();
                }
            } finally {
                leave(one(), chulsu);
                leave(one(), younghee);
            }
        }

        @Test
        @DisplayName("[다른 인스턴스] 다른 인스턴스에서 새 방으로 갈아타면 새 연결은 깨끗하고, 옛 인스턴스의 옛 연결은 닫힌다")
        void crossInstanceRoomSwitch() {
            TestUser chulsu = signUpAndOnboard();
            TestUser younghee = signUpAndOnboard();
            int oldRoom = apply(one(), chulsu, TOMORROW.atTime(20, 0), 5000).number("runningRoomId");
            apply(one(), younghee, TOMORROW.atTime(20, 0), 5000);

            try (SseStream oldStream = openMatchStreamOn(one(), chulsu.accessToken())) {
                oldStream.await("MATCH_ROOM_ENTRANCE");

                // 철수가 나가기·재신청·새 스트림을 모두 다른 인스턴스로 한다(로드 밸런서가 그렇게 보낸 경우)
                leave(two(), chulsu);
                int newRoom = apply(two(), chulsu, TOMORROW.atTime(21, 0), 5000).number("runningRoomId");
                try (SseStream newStream = openMatchStreamOn(two(), chulsu.accessToken())) {
                    assertThat(newStream.await("MATCH_ROOM_ENTRANCE").get("runningRoomId")).isEqualTo(newRoom);
                    chat(two(), younghee, oldRoom, "옛 방에서 보낸 말");

                    // then - 새 인스턴스는 옛 방을 구독하지 않아 새 연결은 깨끗하다
                    assertThat(newStream.drainFor(QUIET).stream().filter(event -> roomOf(event) == oldRoom).toList())
                            .as("새 연결로 새어 들어온 옛 방 이벤트").isEmpty();
                    // then - 옛 인스턴스의 옛 연결은 대체됐으니 닫혀야 하고, 옛 방 채팅을 계속 받으면 안 된다
                    List<Map<String, Object>> oldEvents = oldStream.drainFor(QUIET);
                    assertThat(namesOf(oldEvents)).as("옛 인스턴스의 옛 연결이 받은 것").doesNotContain("ROOM_CHAT_SENT");
                    assertThat(namesOf(oldEvents)).as("옛 인스턴스가 옛 연결을 닫았는가").contains(SseStream.ENDED);
                }
            } finally {
                leave(one(), chulsu);
                leave(one(), younghee);
            }
        }

        @Test
        @DisplayName("[다른 인스턴스] 같은 방으로 다른 인스턴스에 다시 붙으면 옛 연결이 닫혀 이벤트가 두 번 오지 않는다")
        void crossInstanceSameRoomReconnect() {
            TestUser chulsu = signUpAndOnboard();
            TestUser younghee = signUpAndOnboard();
            int room = apply(one(), chulsu, TOMORROW.atTime(22, 0), 5000).number("runningRoomId");
            apply(one(), younghee, TOMORROW.atTime(22, 0), 5000);

            try (SseStream oldStream = openMatchStreamOn(one(), chulsu.accessToken())) {
                oldStream.await("MATCH_ROOM_ENTRANCE");
                // 앱 재시작처럼 같은 방으로 새 연결이 붙는데, 이번에는 다른 인스턴스다
                try (SseStream newStream = openMatchStreamOn(two(), chulsu.accessToken())) {
                    newStream.await("MATCH_ROOM_ENTRANCE");
                    chat(one(), younghee, room, "같은 방 메시지");

                    // then - 새 연결은 받는다
                    assertThat(newStream.await("ROOM_CHAT_SENT").get("runningRoomId")).isEqualTo(room);
                    // then - 옛 연결은 닫혀야 하고 같은 메시지를 또 받으면 안 된다
                    List<Map<String, Object>> oldEvents = oldStream.drainFor(QUIET);
                    assertThat(namesOf(oldEvents)).as("옛 연결이 받은 것").doesNotContain("ROOM_CHAT_SENT");
                    assertThat(namesOf(oldEvents)).as("옛 인스턴스가 옛 연결을 닫았는가").contains(SseStream.ENDED);
                }
            } finally {
                leave(one(), chulsu);
                leave(one(), younghee);
            }
        }

        @Test
        @DisplayName("[다른 인스턴스] 채팅은 인스턴스를 넘어 상대에게 가고 보낸 사람에게는 가지 않는다")
        void chatCrossesInstances() {
            TestUser chulsu = signUpAndOnboard();
            TestUser younghee = signUpAndOnboard();
            int room = apply(one(), chulsu, TOMORROW.atTime(18, 0), 10000).number("runningRoomId");
            apply(two(), younghee, TOMORROW.atTime(18, 0), 10000);

            try (SseStream chulsuStream = openMatchStreamOn(one(), chulsu.accessToken());
                 SseStream youngheeStream = openMatchStreamOn(two(), younghee.accessToken())) {
                chulsuStream.await("MATCH_ROOM_ENTRANCE");
                youngheeStream.await("MATCH_ROOM_ENTRANCE");

                // when - 영희가 인스턴스 2로, 철수가 인스턴스 1로 보낸다
                Response fromYounghee = chat(two(), younghee, room, "2번에서 보냄");
                Response fromChulsu = chat(one(), chulsu, room, "1번에서 보냄");

                // then - 각자 상대 것만 받는다(자기 것이 먼저 왔다면 첫 이벤트가 자기 chatId다)
                assertThat(chulsuStream.await("ROOM_CHAT_SENT")).isEqualTo(fromYounghee.body());
                assertThat(youngheeStream.await("ROOM_CHAT_SENT")).isEqualTo(fromChulsu.body());
            } finally {
                leave(one(), chulsu);
                leave(one(), younghee);
            }
        }

        @Test
        @DisplayName("[다른 인스턴스] 다른 인스턴스로 들어온 참가자가 이 인스턴스 스트림의 인원 변동으로 보인다")
        void roomUpdateCrossesInstances() {
            TestUser chulsu = signUpAndOnboard();
            TestUser younghee = signUpAndOnboard();
            apply(one(), chulsu, TOMORROW.atTime(19, 0), 10000);

            try (SseStream chulsuStream = openMatchStreamOn(one(), chulsu.accessToken())) {
                chulsuStream.await("MATCH_ROOM_ENTRANCE");

                // when - 영희가 인스턴스 2로 같은 슬롯에 신청한다
                apply(two(), younghee, TOMORROW.atTime(19, 0), 10000);

                // then
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> players =
                        (List<Map<String, Object>>) chulsuStream.await("MATCH_ROOM_UPDATED").get("players");
                assertThat(players).extracting(player -> player.get("userId"))
                        .containsExactlyInAnyOrder(chulsu.userId(), younghee.userId());
            } finally {
                leave(one(), chulsu);
                leave(one(), younghee);
            }
        }
    }

    @Nested
    @DisplayName("러닝 WebSocket")
    class RunningSocketTest {

        private static final Duration CLOSE_TIMEOUT = Duration.ofSeconds(10);

        private long openSolo(TestUser user) {
            Response opened = postOn(one(), "/running-rooms/solo", Map.of(), user.accessToken());
            assertThat(opened.status()).as("솔로 방 %s", opened.body()).isEqualTo(201);
            return opened.number("runningRoomId");
        }

        @Test
        @DisplayName("[같은 인스턴스] 같은 사람이 다시 붙으면 옛 연결을 바로 닫는다")
        void sameInstanceReconnectClosesOld() {
            TestUser runner = signUpAndOnboard();
            long room = openSolo(runner);

            try (RunningWebSocket oldSocket = connectRunningWebSocketOn(one(), runner.accessToken())) {
                oldSocket.send("RUNNING_START", Map.of("runningRoomId", room));
                oldSocket.await("RUNNING_STARTED");
                try (RunningWebSocket newSocket = connectRunningWebSocketOn(one(), runner.accessToken())) {
                    newSocket.send("RUNNING_START", Map.of("runningRoomId", room));
                    newSocket.await("RUNNING_STARTED");

                    // then
                    oldSocket.awaitClosed(CLOSE_TIMEOUT);
                }
            }
        }

        @Test
        @DisplayName("[다른 인스턴스] 같은 사람이 다른 인스턴스로 다시 붙으면 SUPERSEDE로 옛 인스턴스의 연결이 닫힌다")
        void crossInstanceReconnectClosesOld() {
            TestUser runner = signUpAndOnboard();
            long room = openSolo(runner);

            try (RunningWebSocket oldSocket = connectRunningWebSocketOn(one(), runner.accessToken())) {
                oldSocket.send("RUNNING_START", Map.of("runningRoomId", room));
                oldSocket.await("RUNNING_STARTED");
                try (RunningWebSocket newSocket = connectRunningWebSocketOn(two(), runner.accessToken())) {
                    newSocket.send("RUNNING_START", Map.of("runningRoomId", room));
                    newSocket.await("RUNNING_STARTED");

                    // then - 인스턴스 2가 방 채널로 알린 대체 통지를 인스턴스 1이 받아 닫는다
                    oldSocket.awaitClosed(CLOSE_TIMEOUT);
                }
            }
        }

        @Test
        @DisplayName("[다른 인스턴스] 다른 인스턴스에 붙은 같은 방 참가자에게 진행 통지가 간다")
        void progressCrossesInstances() {
            TestUser chulsu = signUpAndOnboard();
            TestUser younghee = signUpAndOnboard();
            int room = apply(one(), chulsu, TOMORROW.atTime(21, 0), 10000).number("runningRoomId");
            assertThat(apply(two(), younghee, TOMORROW.atTime(21, 0), 10000).number("runningRoomId")).isEqualTo(room);
            // 시작 스케줄러를 기다리지 않고 확정·시작 시각 경과 상태로 만든다 — RUNNING_START가 방을 STARTED로 올린다
            executeSql("UPDATE running_rooms SET status = 'MATCHED', start_at = (now() AT TIME ZONE 'Asia/Seoul') "
                    + "- interval '1 minute' WHERE running_room_id = " + room);

            try (RunningWebSocket chulsuSocket = connectRunningWebSocketOn(one(), chulsu.accessToken());
                 RunningWebSocket youngheeSocket = connectRunningWebSocketOn(two(), younghee.accessToken())) {
                chulsuSocket.send("RUNNING_START", Map.of("runningRoomId", room));
                chulsuSocket.await("RUNNING_STARTED");
                youngheeSocket.send("RUNNING_START", Map.of("runningRoomId", room));
                youngheeSocket.await("RUNNING_STARTED");

                // when - 철수(인스턴스 1)가 좌표를 보낸다
                chulsuSocket.send("RUNNING_LOCATION_UPDATE", Map.of("locations", track(10)));

                // then - 영희(인스턴스 2)가 철수의 진행 통지를 받는다
                Map<String, Object> progress = youngheeSocket.await("RUNNING_PROGRESS_UPDATED", Duration.ofSeconds(15));
                assertThat(progress.toString()).contains(chulsu.userId());
            }
        }

        @Test
        @DisplayName("[다른 인스턴스] 연달아 A(1)→B(2)→C(1)로 다시 붙어도 마지막 연결만 남고, 상대 진행 통지는 마지막 연결로 간다")
        void pingPongReconnectKeepsOnlyLatest() {
            TestUser chulsu = signUpAndOnboard();
            TestUser younghee = signUpAndOnboard();
            long room = startedRoom(chulsu, younghee, TOMORROW.atTime(20, 0), 3000);

            try (RunningWebSocket youngheeSocket = start(two(), younghee, room);
                 RunningWebSocket a = start(one(), chulsu, room);
                 RunningWebSocket b = start(two(), chulsu, room)) {
                a.awaitClosed(CLOSE_TIMEOUT);
                try (RunningWebSocket c = start(one(), chulsu, room)) {
                    // then - 인스턴스 2는 C의 대체 통지로 B를 닫는다. 인스턴스 1도 자기가 보낸 그 통지를 받지만
                    //        통지에 이긴 연결 ID(C)가 실려 있어 C는 닫지 않는다
                    b.awaitClosed(CLOSE_TIMEOUT);

                    // when - 영희(인스턴스 2)가 좌표를 보낸다
                    youngheeSocket.send("RUNNING_LOCATION_UPDATE", Map.of("locations", track(10)));

                    // then - 철수의 마지막 연결 C(인스턴스 1)가 영희의 진행을 받는다
                    Map<String, Object> progress = c.await("RUNNING_PROGRESS_UPDATED", Duration.ofSeconds(15));
                    assertThat(progress.toString()).contains(younghee.userId());
                    assertThat(namesOf(c.drainFor(Duration.ofSeconds(1)))).doesNotContain(RunningWebSocket.CLOSED_EVENT);
                }
            }
        }

        @Test
        @DisplayName("[같은 인스턴스] 같은 인스턴스로 다시 붙으면 상대에게 '끊김'이 보이지 않는다")
        void sameInstanceReconnectIsNotSeenAsDisconnected() {
            TestUser chulsu = signUpAndOnboard();
            TestUser younghee = signUpAndOnboard();
            long room = startedRoom(chulsu, younghee, TOMORROW.atTime(21, 0), 3000);

            try (RunningWebSocket youngheeSocket = start(two(), younghee, room);
                 RunningWebSocket oldSocket = start(one(), chulsu, room)) {
                youngheeSocket.drainFor(Duration.ofSeconds(1));   // 시작 무렵의 상태 통지는 흘린다
                try (RunningWebSocket newSocket = start(one(), chulsu, room)) {
                    oldSocket.awaitClosed(CLOSE_TIMEOUT);

                    // then - 옛 연결 정리는 가드에 걸려(연결표가 이미 새 연결) 끊김을 알리지 않는다
                    assertThat(statusesOf(youngheeSocket.drainFor(QUIET), chulsu)).doesNotContain("DISCONNECTED");
                }
            }
        }

        // 다른 인스턴스로 다시 붙으면 옛 인스턴스가 옛 연결을 닫으며 DISCONNECTED를 늦게 보낼 수 있다 —
        // 막지 않고 둔다(api-spec 5-D 전이 규칙). 재연결만으로는 인정하지 않고 새 연결의 메시지가 바로잡는다
        @Test
        @DisplayName("[다른 인스턴스] 다른 인스턴스로 다시 붙은 뒤 새 연결로 좌표를 보내면 상대에게 다시 RUNNING으로 보인다")
        void crossInstanceReconnectRecoversOnNextCoordinates() {
            TestUser chulsu = signUpAndOnboard();
            TestUser younghee = signUpAndOnboard();
            long room = startedRoom(chulsu, younghee, TOMORROW.atTime(18, 0), 5000);

            try (RunningWebSocket youngheeSocket = start(two(), younghee, room);
                 RunningWebSocket oldSocket = start(one(), chulsu, room)) {
                try (RunningWebSocket newSocket = start(two(), chulsu, room)) {
                    oldSocket.awaitClosed(CLOSE_TIMEOUT);
                    List<String> afterReconnect = statusesOf(youngheeSocket.drainFor(QUIET), chulsu);

                    // when - 새 연결로 처음 보는 좌표를 보낸다
                    newSocket.send("RUNNING_LOCATION_UPDATE", Map.of("locations", track(10)));
                    List<String> afterCoordinates = statusesOf(youngheeSocket.drainFor(QUIET), chulsu);

                    // then - 끊김이 보였더라도 다음 좌표 배치가 RUNNING으로 되돌린다
                    System.out.println("[관찰] 재연결 직후 영희가 본 철수 상태: " + afterReconnect
                            + " / 새 좌표 뒤: " + afterCoordinates);
                    assertThat(afterCoordinates).isNotEmpty();
                    assertThat(afterCoordinates.getLast()).isEqualTo("RUNNING");
                }
            }
        }

        // 두 사람이 든 매칭 방을 "확정·시작 시각 경과"로 만든다 — RUNNING_START가 방을 STARTED로 올린다
        private long startedRoom(TestUser first, TestUser second, LocalDateTime slot, int distance) {
            int room = apply(one(), first, slot, distance).number("runningRoomId");
            assertThat(apply(two(), second, slot, distance).number("runningRoomId")).isEqualTo(room);
            executeSql("UPDATE running_rooms SET status = 'MATCHED', start_at = (now() AT TIME ZONE 'Asia/Seoul') "
                    + "- interval '1 minute' WHERE running_room_id = " + room);
            return room;
        }

        private RunningWebSocket start(String baseUrl, TestUser user, long room) {
            RunningWebSocket socket = connectRunningWebSocketOn(baseUrl, user.accessToken());
            socket.send("RUNNING_START", Map.of("runningRoomId", room));
            socket.await("RUNNING_STARTED");
            return socket;
        }

        @SuppressWarnings("unchecked")
        private static List<String> statusesOf(List<Map<String, Object>> messages, TestUser user) {
            return messages.stream()
                    .filter(message -> "RUNNING_PROGRESS_UPDATED".equals(message.get("event")))
                    .map(message -> (Map<String, Object>) message.get("data"))
                    .filter(data -> user.userId().equals(data.get("userId")))
                    .map(data -> (String) data.get("status"))
                    .toList();
        }

        // 정북으로 5m씩 2초 간격 — 시작은 지금보다 앞선 시각이라 서버 시각 필터를 넘는다
        private List<Map<String, Object>> track(int count) {
            LocalDateTime start = LocalDateTime.now(APP_ZONE).minusSeconds(count * 2L);
            List<Map<String, Object>> points = new ArrayList<>();
            for (int sequence = 0; sequence < count; sequence++) {
                Map<String, Object> point = new HashMap<>();
                point.put("sequence", sequence);
                point.put("latitude", 37.5665 + sequence * 5.0 / 111_320.0);
                point.put("longitude", 126.9780);
                point.put("altitudeMeters", 30.0);
                point.put("accuracyMeters", 5.0);
                point.put("speedMetersPerSecond", 2.5);
                point.put("headingDegrees", 0.0);
                point.put("cadenceSpm", 170);
                point.put("currentPaceSecondsPerKm", 400);
                point.put("pauseSegment", 0);
                point.put("recordedAt", start.plusSeconds(sequence * 2L).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
                points.add(point);
            }
            return points;
        }
    }
}
