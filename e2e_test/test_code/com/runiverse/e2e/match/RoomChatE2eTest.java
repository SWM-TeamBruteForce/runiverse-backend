package com.runiverse.e2e.match;

import com.runiverse.e2e.E2eTestSupport;
import com.runiverse.e2e.SseStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

// 대기방 채팅 보내기(5-E-2)·목록(5-E-1)과 ROOM_CHAT_SENT — 실제 DB(IDENTITY·created_at·JPQL)·Redis 왕복·보안 필터를 함께 탄다
@DisplayName("배포 이미지 대상 대기방 채팅 E2E 테스트")
class RoomChatE2eTest extends E2eTestSupport {

    private static final ZoneId APP_ZONE = ZoneId.of("Asia/Seoul");
    // 다른 매칭 E2E와 한 방에 합쳐지지 않게 아무도 쓰지 않는 내일 슬롯·거리를 쓴다.
    // 매칭 시작 시각은 정시 간격이라(match.allowed-start-time-interval) 정시만 고른다
    private static final LocalDateTime CHAT_SLOT = LocalDate.now(APP_ZONE).plusDays(1).atTime(22, 0);
    private static final int DISTANCE = 5000;

    @Test
    @DisplayName("보낸 채팅은 201로 돌아오고 같은 방 다른 참가자에게만 ROOM_CHAT_SENT로 간다")
    void sentChatReachesOthersButNotSender() {
        // given - 두 사람이 같은 매칭 방에 있고 둘 다 스트림을 열었다
        TestUser host = signUpAndOnboard();
        TestUser guest = signUpAndOnboard();
        int runningRoomId = apply(host).number("runningRoomId");
        assertThat(apply(guest).number("runningRoomId")).isEqualTo(runningRoomId);

        try (SseStream hostStream = openMatchStream(host.accessToken());
             SseStream guestStream = openMatchStream(guest.accessToken())) {
            // 입장 이벤트는 채팅과 무관하다 — 연결이 자리 잡았는지만 확인한다
            assertThat(hostStream.await("MATCH_ROOM_ENTRANCE").get("runningRoomId")).isEqualTo(runningRoomId);
            assertThat(guestStream.await("MATCH_ROOM_ENTRANCE").get("runningRoomId")).isEqualTo(runningRoomId);

            // when - guest가 보낸다
            Response sent = post(chatsPath(runningRoomId), Map.of("content", "곧 출발해요"), guest.accessToken());

            // then - 응답이 명세의 Chat이다. ID·보낸 시각은 실제 DB가 만든다
            assertThat(sent.status()).isEqualTo(201);
            int chatId = sent.number("chatId");
            assertThat(sent.number("runningRoomId")).isEqualTo(runningRoomId);
            assertThat(sent.text("content")).isEqualTo("곧 출발해요");
            assertThat(sent.bool("isDeleted")).isFalse();
            assertThat(sent.body()).containsEntry("editedAt", null);
            assertThat(LocalDateTime.parse(sent.text("createdAt"))).isNotNull();
            assertThat(sender(sent.body()))
                    .containsEntry("userId", guest.userId())
                    .containsEntry("nickname", guest.nickname())
                    .containsEntry("isDeleted", false);

            // then - host는 Redis를 돌아온 같은 채팅을 받는다
            Map<String, Object> received = hostStream.await("ROOM_CHAT_SENT");
            assertThat(received).isEqualTo(sent.body());

            // then - guest는 자기 채팅을 받지 않는다: 뒤이어 host가 보낸 것이 guest의 첫 채팅 이벤트다
            Response reply = post(chatsPath(runningRoomId), Map.of("content", "네 좋아요"), host.accessToken());
            assertThat(reply.status()).isEqualTo(201);
            Map<String, Object> guestFirst = guestStream.await("ROOM_CHAT_SENT");
            assertThat(((Number) guestFirst.get("chatId")).intValue()).isEqualTo(reply.number("chatId"));
            assertThat(reply.number("chatId")).isGreaterThan(chatId);   // 생성 순서대로 커진다

            // then - 저장된 행이 응답과 같다
            List<String> rows = querySql("""
                    SELECT running_room_id, user_id, content, edited_at IS NULL, deleted_at IS NULL
                    FROM running_room_chats WHERE running_room_chat_id = %d
                    """.formatted(chatId));
            assertThat(rows).containsExactly("%d|%s|곧 출발해요|t|t".formatted(runningRoomId, guest.userId()));
        } finally {
            leave(host);
            leave(guest);
        }
    }

    @Test
    @DisplayName("친구를 초대해 수락하면 초대 방에서도 서로의 채팅을 ROOM_CHAT_SENT로 받는다")
    void inviteRoomChatFlowsBothWays() {
        // given - 친구 요청·수락 → 초대 방 개설 → 초대 → 수락
        TestUser host = signUpAndOnboard();
        TestUser friend = signUpAndOnboard();
        assertThat(post("/users/" + friend.userId() + "/friend-request", null, host.accessToken()).status())
                .isBetween(200, 299);
        assertThat(put("/users/" + host.userId() + "/friend-request", friend.accessToken()).status())
                .isBetween(200, 299);
        Response opened = post("/match-rooms/invite", Map.of(
                "scheduledStartAt", inviteStartOrSkip().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                "targetDistanceMeters", 7_000), host.accessToken());
        assertThat(opened.status()).isEqualTo(201);
        int runningRoomId = opened.number("runningRoomId");
        assertThat(post("/match-rooms/" + runningRoomId + "/invitations",
                Map.of("inviteeId", friend.userId()), host.accessToken()).status()).isEqualTo(204);
        assertThat(post("/match-rooms/" + runningRoomId + "/invitations/accept", null, friend.accessToken()).status())
                .isEqualTo(201);

        try (SseStream hostStream = openMatchStream(host.accessToken());
             SseStream friendStream = openMatchStream(friend.accessToken())) {
            // 초대 방은 입장 이벤트 이름이 다르지만 채팅 이벤트 이름은 같다
            hostStream.await("INVITE_ROOM_ENTRANCE");
            friendStream.await("INVITE_ROOM_ENTRANCE");

            // when - 서로 한 번씩 보낸다
            Response fromHost = post(chatsPath(runningRoomId), Map.of("content", "어디쯤이야?"), host.accessToken());
            Response fromFriend = post(chatsPath(runningRoomId), Map.of("content", "거의 다 왔어"), friend.accessToken());

            // then - 각자 상대의 채팅만 받는다(자기 것이 먼저 왔다면 첫 이벤트가 자기 chatId다)
            assertThat(fromHost.status()).isEqualTo(201);
            assertThat(fromFriend.status()).isEqualTo(201);
            assertThat(friendStream.await("ROOM_CHAT_SENT")).isEqualTo(fromHost.body());
            assertThat(hostStream.await("ROOM_CHAT_SENT")).isEqualTo(fromFriend.body());
        } finally {
            leave(host);
            leave(friend);
        }
    }

    @Test
    @DisplayName("방 밖이면 404, 빈 내용은 400, 토큰이 없으면 401이다")
    void rejectsOutsidersAndBadRequests() {
        // given
        TestUser member = signUpAndOnboard();
        TestUser outsider = signUpAndOnboard();
        int runningRoomId = apply(member).number("runningRoomId");

        try {
            // when
            Response notInRoom = post(chatsPath(runningRoomId), Map.of("content", "안녕하세요"), outsider.accessToken());
            Response blank = post(chatsPath(runningRoomId), Map.of("content", "   "), member.accessToken());
            Response tooLong = post(chatsPath(runningRoomId), Map.of("content", "가".repeat(201)), member.accessToken());
            Response noToken = post(chatsPath(runningRoomId), Map.of("content", "안녕하세요"), null);

            // then - 남의 방이 있는지 없는지를 알려 주지 않는다
            assertThat(notInRoom.status()).isEqualTo(404);
            assertThat(notInRoom.text("code")).isEqualTo("NOT_FOUND");
            assertThat(blank.status()).isEqualTo(400);
            assertThat(blank.text("message")).isEqualTo("메시지 내용은 필수입니다.");
            assertThat(tooLong.status()).isEqualTo(400);
            assertThat(tooLong.text("message")).isEqualTo("메시지는 200자 이하여야 합니다.");
            assertThat(noToken.status()).isEqualTo(401);
            assertThat(querySql("SELECT count(*) FROM running_room_chats WHERE running_room_id = " + runningRoomId))
                    .containsExactly("0");
        } finally {
            leave(member);
        }
    }

    @Test
    @DisplayName("러닝이 시작된 방에서는 배정이 남아 있어도 409 ROOM_CHAT_CLOSED다")
    void closedAfterRunningStarts() {
        // given - 시작 스케줄러를 기다리지 않고 방 상태만 시작으로 바꾼다(배정·신청은 그대로다)
        TestUser member = signUpAndOnboard();
        int runningRoomId = apply(member).number("runningRoomId");
        executeSql("UPDATE running_rooms SET status = 'STARTED' WHERE running_room_id = " + runningRoomId);

        try {
            // when
            Response response = post(chatsPath(runningRoomId), Map.of("content", "출발!"), member.accessToken());

            // then - 가려져 500이 되면 클라가 채팅 입력을 닫을지 가를 수 없다
            assertThat(response.status()).isEqualTo(409);
            assertThat(response.text("code")).isEqualTo("ROOM_CHAT_CLOSED");
            assertThat(response.text("message")).isEqualTo("지금은 채팅할 수 없습니다.");
        } finally {
            // 다른 테스트가 같은 슬롯에 붙지 않도록 모집 중으로 되돌린 뒤 나간다
            executeSql("UPDATE running_rooms SET status = 'MATCHING' WHERE running_room_id = " + runningRoomId);
            leave(member);
        }
    }

    @Test
    @DisplayName("채팅 목록은 최신순 커서로 넘기고, 늦게 들어온 사람도 방의 첫 메시지까지 본다")
    void listPagesNewestFirstForLateJoiner() {
        // given - host 혼자 있는 방에 세 건이 쌓인 뒤 guest가 들어온다
        TestUser host = signUpAndOnboard();
        TestUser guest = signUpAndOnboard();
        int runningRoomId = apply(host).number("runningRoomId");
        Response first = post(chatsPath(runningRoomId), Map.of("content", "하나"), host.accessToken());
        Response second = post(chatsPath(runningRoomId), Map.of("content", "둘"), host.accessToken());
        Response third = post(chatsPath(runningRoomId), Map.of("content", "셋"), host.accessToken());
        assertThat(apply(guest).number("runningRoomId")).isEqualTo(runningRoomId);

        try {
            // when - 두 개씩 끊어 읽는다
            Response page1 = get(chatsPath(runningRoomId) + "?limit=2", guest.accessToken());
            String cursor = page1.text("nextCursor");
            Response page2 = get(chatsPath(runningRoomId) + "?limit=2&cursor=" + cursor, guest.accessToken());

            // then - 첫 페이지는 가장 최근 두 건이고 보내기 응답과 같은 모양이다(실제 JPQL·조립을 탄다)
            assertThat(page1.status()).isEqualTo(200);
            assertThat(page1.objects("items")).containsExactly(third.body(), second.body());
            assertThat(cursor).isNotBlank();
            // then - 다음 페이지는 남은 한 건이고 방의 첫 메시지라 커서가 끝난다
            assertThat(page2.status()).isEqualTo(200);
            assertThat(page2.objects("items")).containsExactly(first.body());
            assertThat(page2.body()).containsEntry("nextCursor", null);
            // then - limit 없이 부르면 한 번에 다 온다(기본 20)
            assertThat(get(chatsPath(runningRoomId), guest.accessToken()).objects("items"))
                    .extracting(item -> item.get("content")).containsExactly("셋", "둘", "하나");
        } finally {
            leave(host);
            leave(guest);
        }
    }

    @Test
    @DisplayName("지운 메시지는 목록에서 자리를 지키되 내용 없이 isDeleted로만 드러나고, 원문은 DB에 남는다")
    void deletedChatKeepsPlaceWithoutContent() {
        // given - 삭제 API는 다음 단계라 DB에서 직접 지운 시각을 찍는다
        TestUser member = signUpAndOnboard();
        int runningRoomId = apply(member).number("runningRoomId");
        int keptId = post(chatsPath(runningRoomId), Map.of("content", "남는 말"), member.accessToken()).number("chatId");
        int deletedId = post(chatsPath(runningRoomId), Map.of("content", "지울 말"), member.accessToken()).number("chatId");
        executeSql("UPDATE running_room_chats SET deleted_at = now() WHERE running_room_chat_id = " + deletedId);

        try {
            // when
            List<Map<String, Object>> items = get(chatsPath(runningRoomId), member.accessToken()).objects("items");

            // then - 순서와 자리는 그대로다
            assertThat(items).extracting(item -> ((Number) item.get("chatId")).intValue())
                    .containsExactly(deletedId, keptId);
            assertThat(items.get(0))
                    .containsEntry("content", null)
                    .containsEntry("isDeleted", true);
            assertThat(items.get(1))
                    .containsEntry("content", "남는 말")
                    .containsEntry("isDeleted", false);
            // then - 원문은 신고 대응용으로 행에 남는다
            assertThat(querySql("SELECT content FROM running_room_chats WHERE running_room_chat_id = " + deletedId))
                    .containsExactly("지울 말");
        } finally {
            leave(member);
        }
    }

    @Test
    @DisplayName("채팅 목록도 방 밖이면 404, 깨진 커서는 400, 러닝이 시작됐으면 409, 토큰이 없으면 401이다")
    void listRejectsLikeSend() {
        // given
        TestUser member = signUpAndOnboard();
        TestUser outsider = signUpAndOnboard();
        int runningRoomId = apply(member).number("runningRoomId");

        try {
            // when & then - 방 판정은 보내기와 같다
            Response notInRoom = get(chatsPath(runningRoomId), outsider.accessToken());
            assertThat(notInRoom.status()).isEqualTo(404);
            assertThat(notInRoom.text("code")).isEqualTo("NOT_FOUND");
            Response brokenCursor = get(chatsPath(runningRoomId) + "?cursor=!!!", member.accessToken());
            assertThat(brokenCursor.status()).isEqualTo(400);
            assertThat(brokenCursor.text("code")).isEqualTo("INVALID_REQUEST");
            assertThat(get(chatsPath(runningRoomId), null).status()).isEqualTo(401);

            executeSql("UPDATE running_rooms SET status = 'STARTED' WHERE running_room_id = " + runningRoomId);
            Response closed = get(chatsPath(runningRoomId), member.accessToken());
            assertThat(closed.status()).isEqualTo(409);
            assertThat(closed.text("code")).isEqualTo("ROOM_CHAT_CLOSED");
        } finally {
            executeSql("UPDATE running_rooms SET status = 'MATCHING' WHERE running_room_id = " + runningRoomId);
            leave(member);
        }
    }

    private Response apply(TestUser user) {
        Response response = post("/match-rooms", Map.of(
                "scheduledStartAt", CHAT_SLOT.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                "targetDistanceMeters", DISTANCE
        ), user.accessToken());
        assertThat(response.status()).isEqualTo(201);
        return response;
    }

    // 초대 방 시작 시각은 오늘(KST) 안이어야 한다 — 늦은 밤에는 고를 시각이 없어 건너뛴다
    private static LocalDateTime inviteStartOrSkip() {
        LocalDateTime now = LocalDateTime.now(APP_ZONE);
        LocalDateTime start = now.plusMinutes(30).truncatedTo(ChronoUnit.MINUTES);
        assumeTrue(!start.isAfter(now.toLocalDate().atTime(23, 59)),
                "KST 23:29 이후에는 오늘 안에 고를 시작 시각이 부족하다");
        return start;
    }

    private void leave(TestUser user) {
        post("/match-rooms/leave", null, user.accessToken());
    }

    private static String chatsPath(int runningRoomId) {
        return "/match-rooms/" + runningRoomId + "/chats";
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> sender(Map<String, Object> chat) {
        return (Map<String, Object>) chat.get("sender");
    }
}
