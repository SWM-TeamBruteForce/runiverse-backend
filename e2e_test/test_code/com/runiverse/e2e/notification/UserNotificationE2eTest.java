package com.runiverse.e2e.notification;

import com.runiverse.e2e.E2eTestSupport;
import com.runiverse.e2e.SseStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

// 쌓인 행은 응답으로 조립되기 전의 저장값을 보려고 DB에서 직접 읽는다.
// 엔티티로 만든 실제 스키마(jsonb·CHECK·FK)를 통과하는지가 함께 확인된다
@DisplayName("배포 이미지 대상 인앱 알림 쌓기·목록·모두 읽음·푸시 E2E 테스트")
class UserNotificationE2eTest extends E2eTestSupport {

    private static final ZoneId APP_ZONE = ZoneId.of("Asia/Seoul");   // 호스트 시간대로 만들면 시작 시각이 어긋난다

    @Test
    @DisplayName("친구 요청을 보내면 받는 사람에게만 요청한 사람을 담은 안 읽은 알림이 쌓인다")
    void friendRequestStoresNotificationForTarget() {
        // given
        TestUser requester = signUpAndOnboard();
        TestUser target = signUpAndOnboard();

        // when
        assertThat(post(friendRequestPath(target), null, requester.accessToken()).status()).isEqualTo(201);

        // then
        assertThat(notificationsOf(target)).containsExactly("FRIEND_REQUESTED|" + requester.userId() + "|false");
        assertThat(notificationsOf(requester)).isEmpty();
    }

    @Test
    @DisplayName("요청을 수락하면 요청했던 사람에게 수락한 사람을 담은 알림이 쌓인다")
    void acceptStoresNotificationForRequester() {
        // given
        TestUser requester = signUpAndOnboard();
        TestUser target = signUpAndOnboard();
        assertThat(post(friendRequestPath(target), null, requester.accessToken()).status()).isEqualTo(201);

        // when
        assertThat(put(friendRequestPath(requester), target.accessToken()).status()).isEqualTo(200);

        // then -> 받은 요청 알림은 사건의 기록이라 수락해도 그대로 남는다
        assertThat(notificationsOf(requester)).containsExactly("FRIEND_ACCEPTED|" + target.userId() + "|false");
        assertThat(notificationsOf(target)).containsExactly("FRIEND_REQUESTED|" + requester.userId() + "|false");

        // 응답을 못 받은 앱의 재요청은 이미 친구라 수락 알림을 또 쌓지 않는다
        assertThat(put(friendRequestPath(requester), target.accessToken()).status()).isEqualTo(200);
        assertThat(notificationsOf(requester)).hasSize(1);
    }

    @Test
    @DisplayName("서로 요청해 바로 친구가 되면 먼저 요청했던 사람에게 수락 알림이 쌓인다")
    void mutualRequestStoresAcceptedNotification() {
        // given
        TestUser first = signUpAndOnboard();
        TestUser second = signUpAndOnboard();
        assertThat(post(friendRequestPath(second), null, first.accessToken()).status()).isEqualTo(201);

        // when -> 상대가 먼저 요청해 뒀으니 새 요청이 아니라 수락이다(200)
        assertThat(post(friendRequestPath(first), null, second.accessToken()).status()).isEqualTo(200);

        // then
        assertThat(notificationsOf(first)).containsExactly("FRIEND_ACCEPTED|" + second.userId() + "|false");
        assertThat(notificationsOf(second)).containsExactly("FRIEND_REQUESTED|" + first.userId() + "|false");
    }

    @Test
    @DisplayName("요청이 실패하면 알림도 쌓이지 않는다")
    void failedRequestStoresNothing() {
        // given
        TestUser requester = signUpAndOnboard();
        TestUser target = signUpAndOnboard();
        assertThat(post(friendRequestPath(target), null, requester.accessToken()).status()).isEqualTo(201);

        // when -> 이미 요청한 사이라 409로 끝난다
        assertThat(post(friendRequestPath(target), null, requester.accessToken()).status()).isEqualTo(409);

        // then -> 첫 요청의 알림 하나뿐이다
        assertThat(notificationsOf(target)).hasSize(1);
    }

    @Test
    @DisplayName("친구를 초대하면 받은 사람에게 초대한 사람과 방을 담은 알림이 쌓이고, 다시 초대하면 하나 더 쌓인다")
    void invitationStoresNotificationPerSend() {
        // given
        TestUser host = signUpAndOnboard();
        TestUser friend = signUpAndOnboard();
        assertThat(post(friendRequestPath(friend), null, host.accessToken()).status()).isEqualTo(201);
        assertThat(put(friendRequestPath(host), friend.accessToken()).status()).isEqualTo(200);
        int roomId = openInviteRoom(host);

        try {
            // when -> 대기 중인 초대를 다시 보내도 된다
            assertThat(invite(host, roomId, friend).status()).isEqualTo(204);
            assertThat(invite(host, roomId, friend).status()).isEqualTo(204);

            // then -> 초대 행은 하나로 횟수만 오르지만 알림은 보낼 때마다 쌓인다
            assertThat(notificationsOf(friend)).containsExactly(
                    "FRIEND_REQUESTED|" + host.userId() + "|false",
                    "RUNNING_INVITED|" + host.userId() + "|false",
                    "RUNNING_INVITED|" + host.userId() + "|false");
            assertThat(querySql("""
                    SELECT DISTINCT payload ->> 'running_room_id'
                    FROM user_notifications
                    WHERE user_id = '%s' AND type = 'RUNNING_INVITED';
                    """.formatted(friend.userId()))).containsExactly(String.valueOf(roomId));
        } finally {
            assertThat(post("/match-rooms/leave", null, host.accessToken()).status()).isEqualTo(204);
        }
    }

    @Test
    @DisplayName("친구가 아닌 사람에게는 초대가 거절되고 알림도 쌓이지 않는다")
    void rejectedInvitationStoresNothing() {
        // given
        TestUser host = signUpAndOnboard();
        TestUser stranger = signUpAndOnboard();
        int roomId = openInviteRoom(host);

        try {
            // when
            Response rejected = invite(host, roomId, stranger);

            // then
            assertThat(rejected.status()).isBetween(400, 499);
            assertThat(notificationsOf(stranger)).isEmpty();
        } finally {
            assertThat(post("/match-rooms/leave", null, host.accessToken()).status()).isEqualTo(204);
        }
    }

    @Test
    @DisplayName("목록은 최신순으로 지금 관계·초대 상태를 붙여 돌려주고, 안 읽은 수를 함께 준다")
    void listShowsItemsWithCurrentStateAndUnreadCount() {
        // given -> 친구 요청(→수락) 뒤 초대를 받았다
        TestUser host = signUpAndOnboard();
        TestUser friend = signUpAndOnboard();
        assertThat(post(friendRequestPath(friend), null, host.accessToken()).status()).isEqualTo(201);
        assertThat(put(friendRequestPath(host), friend.accessToken()).status()).isEqualTo(200);
        int roomId = openInviteRoom(host);

        try {
            assertThat(invite(host, roomId, friend).status()).isEqualTo(204);

            // when
            Response listed = get("/users/me/notifications", friend.accessToken());

            // then -> 최신(초대)이 먼저다. 요청 알림의 관계는 쌓일 때가 아니라 지금(FRIEND) 값이다
            assertThat(listed.status()).isEqualTo(200);
            assertThat(listed.number("unreadCount")).isEqualTo(2);
            assertThat(listed.text("nextCursor")).isNull();
            List<Map<String, Object>> items = listed.objects("items");
            assertThat(items).extracting(item -> item.get("type"))
                    .containsExactly("RUNNING_INVITED", "FRIEND_REQUESTED");
            Map<String, Object> invited = items.get(0);
            assertThat(invited.get("isRead")).isEqualTo(false);
            assertThat(actorOf(invited))
                    .containsEntry("userId", host.userId())
                    .containsEntry("nickname", host.nickname())
                    .containsEntry("isDeleted", false)
                    .containsEntry("relationshipType", "FRIEND");
            assertThat(invitationOf(invited))
                    .containsEntry("runningRoomId", roomId)
                    .containsEntry("status", "PENDING")
                    .containsEntry("targetDistanceMeters", 7_000);
            assertThat(actorOf(items.get(1))).containsEntry("relationshipType", "FRIEND");
            assertThat(items.get(1).get("invitation")).isNull();
        } finally {
            assertThat(post("/match-rooms/leave", null, host.accessToken()).status()).isEqualTo(204);
        }
    }

    @Test
    @DisplayName("limit만큼 잘라 다음 커서를 주고, 그 커서로 이어서 읽으면 겹치거나 빠지지 않는다")
    void pagesWithCursor() {
        // given -> 서로 다른 세 사람에게 요청을 받았다
        TestUser me = signUpAndOnboard();
        List<TestUser> requesters = List.of(signUpAndOnboard(), signUpAndOnboard(), signUpAndOnboard());
        for (TestUser requester : requesters) {
            assertThat(post(friendRequestPath(me), null, requester.accessToken()).status()).isEqualTo(201);
        }

        // when
        Response first = get("/users/me/notifications?limit=2", me.accessToken());
        Response second = get("/users/me/notifications?limit=2&cursor=" + first.text("nextCursor"), me.accessToken());

        // then -> 최신순이라 마지막 요청자가 먼저다
        assertThat(actorIdsOf(first)).containsExactly(requesters.get(2).userId(), requesters.get(1).userId());
        assertThat(actorIdsOf(second)).containsExactly(requesters.get(0).userId());
        assertThat(second.text("nextCursor")).isNull();
        assertThat(second.number("unreadCount")).isEqualTo(3);
    }

    @Test
    @DisplayName("보관 기간이 지난 알림과 되살리지 못한 알림은 목록에서 빠지고, 지난 알림은 세지도 않는다")
    void skipsExpiredAndBrokenRows() {
        // given -> 요청 셋 중 하나는 31일 전으로, 하나는 키가 빠진 payload로 바꾼다
        TestUser me = signUpAndOnboard();
        List<TestUser> requesters = List.of(signUpAndOnboard(), signUpAndOnboard(), signUpAndOnboard());
        for (TestUser requester : requesters) {
            assertThat(post(friendRequestPath(me), null, requester.accessToken()).status()).isEqualTo(201);
        }
        executeSql("""
                UPDATE user_notifications SET created_at = created_at - INTERVAL '31 days'
                WHERE user_id = '%s' AND payload ->> 'request_user_id' = '%s';
                UPDATE user_notifications SET payload = '{"unknown": 1}'
                WHERE user_id = '%s' AND payload ->> 'request_user_id' = '%s';
                """.formatted(me.userId(), requesters.get(0).userId(), me.userId(), requesters.get(1).userId()));

        // when
        Response listed = get("/users/me/notifications", me.accessToken());

        // then -> 깨진 행은 세지만(행 기준) 목록에서는 빠진다. 지난 행은 둘 다에서 빠진다
        assertThat(listed.status()).isEqualTo(200);
        assertThat(actorIdsOf(listed)).containsExactly(requesters.get(2).userId());
        assertThat(listed.number("unreadCount")).isEqualTo(2);
    }

    @Test
    @DisplayName("모두 읽음은 내 안 읽은 알림만 읽음으로 바꾸고 갱신 시각을 남기며, 다시 불러도 성공한다")
    void readAllMarksOnlyMyUnread() {
        // given -> 나는 요청 둘을 받았고, 요청한 사람은 수락 알림 하나를 받았다
        TestUser me = signUpAndOnboard();
        TestUser first = signUpAndOnboard();
        TestUser second = signUpAndOnboard();
        assertThat(post(friendRequestPath(me), null, first.accessToken()).status()).isEqualTo(201);
        assertThat(post(friendRequestPath(me), null, second.accessToken()).status()).isEqualTo(201);
        assertThat(put(friendRequestPath(first), me.accessToken()).status()).isEqualTo(200);

        // when
        assertThat(post("/users/me/notifications/read", null, me.accessToken()).status()).isEqualTo(204);

        // then -> 목록 항목이 읽음으로 바뀌고 배지는 0이다. 남의 알림은 그대로다
        Response listed = get("/users/me/notifications", me.accessToken());
        assertThat(listed.number("unreadCount")).isZero();
        assertThat(listed.objects("items")).extracting(item -> item.get("isRead")).containsOnly(true);
        assertThat(get("/users/me/notifications", first.accessToken()).number("unreadCount")).isEqualTo(1);
        // 벌크 갱신이 @UpdateTimestamp를 거치지 않아도 갱신 시각이 바뀌었다
        assertThat(querySql("""
                SELECT count(*) FROM user_notifications
                WHERE user_id = '%s' AND updated_at <= created_at;
                """.formatted(me.userId()))).containsExactly("0");

        // 읽을 게 없어도 성공한다
        assertThat(post("/users/me/notifications/read", null, me.accessToken()).status()).isEqualTo(204);
    }

    @Test
    @DisplayName("친구 요청을 받으면 앱 스트림으로 FRIEND_REQUESTED 이벤트가 오고, data는 목록 항목에서 type만 뺀 모양이다")
    void pushesNotificationToRecipientStream() {
        // given -> 받는 사람만 앱 스트림을 열어 둔다
        TestUser requester = signUpAndOnboard();
        TestUser target = signUpAndOnboard();

        try (SseStream targetStream = openAppStream(target.accessToken());
             SseStream requesterStream = openAppStream(requester.accessToken())) {
            assertThat(targetStream.status()).isEqualTo(200);

            // when -> 커밋 뒤 Redis를 거쳐 받는 사람이 붙은 인스턴스가 보낸다
            assertThat(post(friendRequestPath(target), null, requester.accessToken()).status()).isEqualTo(201);

            // then
            Map<String, Object> pushed = targetStream.await("FRIEND_REQUESTED");
            Map<String, Object> listed = new HashMap<>(get("/users/me/notifications", target.accessToken()).objects("items").getFirst());
            // 종류는 event 이름이 말해 data에는 type이 없다 — 나머지는 목록 항목과 같다
            assertThat(listed.remove("type")).isEqualTo("FRIEND_REQUESTED");
            assertThat(pushed).doesNotContainKey("type").isEqualTo(listed);
            assertThat(actorOf(pushed))
                    .containsEntry("userId", requester.userId())
                    .containsEntry("relationshipType", "FRIEND_REQUEST_RECEIVED");

            // 수락하면 요청했던 사람에게 수락 알림이 간다
            assertThat(put(friendRequestPath(requester), target.accessToken()).status()).isEqualTo(200);
            Map<String, Object> accepted = requesterStream.await("FRIEND_ACCEPTED");
            assertThat(accepted).doesNotContainKey("type");
            assertThat(actorOf(accepted))
                    .containsEntry("userId", target.userId())
                    .containsEntry("relationshipType", "FRIEND");
        }
    }

    @Test
    @DisplayName("요청이 실패하면 받는 사람의 스트림에 아무것도 오지 않는다")
    void failedRequestPushesNothing() {
        // given
        TestUser requester = signUpAndOnboard();
        TestUser target = signUpAndOnboard();
        assertThat(post(friendRequestPath(target), null, requester.accessToken()).status()).isEqualTo(201);

        try (SseStream targetStream = openAppStream(target.accessToken())) {
            // when -> 이미 요청한 사이라 409로 롤백된다
            assertThat(post(friendRequestPath(target), null, requester.accessToken()).status()).isEqualTo(409);
            // 뒤이어 성공한 다른 요청의 알림이 첫 이벤트로 와야 실패한 요청이 아무것도 보내지 않은 것이다
            TestUser other = signUpAndOnboard();
            assertThat(post(friendRequestPath(target), null, other.accessToken()).status()).isEqualTo(201);

            // then
            assertThat(actorOf(targetStream.await("FRIEND_REQUESTED"))).containsEntry("userId", other.userId());
        }
    }

    @Test
    @DisplayName("깨진 커서는 400 INVALID_REQUEST다")
    void brokenCursorIsBadRequest() {
        // given
        TestUser me = signUpAndOnboard();

        // when
        Response listed = get("/users/me/notifications?cursor=!!!", me.accessToken());

        // then
        assertThat(listed.status()).isEqualTo(400);
        assertThat(listed.text("code")).isEqualTo("INVALID_REQUEST");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> actorOf(Map<String, Object> item) {
        return (Map<String, Object>) item.get("actor");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> invitationOf(Map<String, Object> item) {
        return (Map<String, Object>) item.get("invitation");
    }

    private static List<Object> actorIdsOf(Response listed) {
        return listed.objects("items").stream().map(item -> actorOf(item).get("userId")).toList();
    }

    private int openInviteRoom(TestUser host) {
        LocalDateTime now = LocalDateTime.now(APP_ZONE);
        LocalDateTime start = now.plusMinutes(30).truncatedTo(ChronoUnit.MINUTES);
        assumeTrue(!start.isAfter(now.toLocalDate().atTime(23, 59)),
                "KST 23:29 이후에는 오늘 안에 고를 시작 시각이 부족하다");
        Response opened = post("/match-rooms/invite", Map.of(
                "scheduledStartAt", start.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                "targetDistanceMeters", 7_000), host.accessToken());
        assertThat(opened.status()).isEqualTo(201);
        return opened.number("runningRoomId");
    }

    private Response invite(TestUser inviter, int roomId, TestUser invitee) {
        return post("/match-rooms/" + roomId + "/invitations", Map.of("inviteeId", invitee.userId()),
                inviter.accessToken());
    }

    private static String friendRequestPath(TestUser target) {
        return "/users/" + target.userId() + "/friend-request";
    }

    // 종류|일으킨 사람|읽음 — 종류마다 키가 달라 아는 키를 차례로 본다
    private List<String> notificationsOf(TestUser user) {
        return querySql("""
                SELECT type
                       || '|' || coalesce(payload ->> 'request_user_id', payload ->> 'accept_user_id')
                       || '|' || is_read
                FROM user_notifications
                WHERE user_id = '%s'
                ORDER BY user_notification_id;
                """.formatted(user.userId()));
    }
}
