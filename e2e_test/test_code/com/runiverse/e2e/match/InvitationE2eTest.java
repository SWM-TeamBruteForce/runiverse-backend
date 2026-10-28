package com.runiverse.e2e.match;

import com.runiverse.e2e.E2eTestSupport;
import com.runiverse.e2e.SseStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

// 초대 행 조회·저장(running_room_invitations)과 친구 확인·받는 사람 상태 조회 JPQL을 실제 DB에서 탄다
@DisplayName("배포 이미지 대상 러닝 초대·수락·거절 E2E 테스트")
class InvitationE2eTest extends E2eTestSupport {

    private static final ZoneId APP_ZONE = ZoneId.of("Asia/Seoul");
    private static final int DISTANCE = 7_000;

    private static LocalDateTime selectableStartOrSkip() {
        LocalDateTime now = LocalDateTime.now(APP_ZONE);
        LocalDateTime start = now.plusMinutes(30).truncatedTo(ChronoUnit.MINUTES);
        assumeTrue(!start.isAfter(now.toLocalDate().atTime(23, 59)),
                "KST 23:29 이후에는 오늘 안에 고를 시작 시각이 부족하다");
        return start;
    }

    private int openRoom(TestUser host) {
        Response opened = post("/match-rooms/invite", Map.of(
                "scheduledStartAt", selectableStartOrSkip().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                "targetDistanceMeters", DISTANCE), host.accessToken());
        assertThat(opened.status()).isEqualTo(201);
        return opened.number("runningRoomId");
    }

    // 요청 → 수락으로 친구를 맺는다
    private void befriend(TestUser a, TestUser b) {
        assertThat(post("/users/" + b.userId() + "/friend-request", null, a.accessToken()).status())
                .isBetween(200, 299);
        assertThat(put("/users/" + a.userId() + "/friend-request", b.accessToken()).status())
                .isBetween(200, 299);
    }

    // 한 번에 한 명이다
    private Response invite(TestUser inviter, int roomId, TestUser invitee) {
        return post("/match-rooms/" + roomId + "/invitations",
                Map.of("inviteeId", invitee.userId()), inviter.accessToken());
    }

    private Response accept(TestUser invitee, int roomId) {
        return post("/match-rooms/" + roomId + "/invitations/accept", null, invitee.accessToken());
    }

    private Response decline(TestUser invitee, int roomId) {
        return post("/match-rooms/" + roomId + "/invitations/decline", null, invitee.accessToken());
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> listOf(Map<String, Object> body, String field) {
        return (List<Map<String, Object>>) body.get(field);
    }

    @Test
    @DisplayName("초대 → 거절 → 다시 초대 → 수락 → 나가기가 스트림과 DB에 이어서 반영된다")
    void inviteDeclineReinviteAcceptLeave() {
        // given
        TestUser host = signUpAndOnboard();
        TestUser friend = signUpAndOnboard();
        befriend(host, friend);
        int roomId = openRoom(host);

        try (SseStream hostStream = openMatchStream(host.accessToken())) {
            assertThat(hostStream.status()).isEqualTo(200);
            hostStream.await("INVITE_ROOM_ENTRANCE");

            // when - 1. 초대 — 초대는 신청이 아니라 받은 사람은 여전히 IDLE이다
            Response sent = invite(host, roomId, friend);
            assertThat(sent.status()).isEqualTo(204);
            assertThat(listOf(hostStream.await("INVITE_ROOM_UPDATED"), "invitations")).singleElement().satisfies(invitee -> {
                assertThat(invitee.get("userId")).isEqualTo(friend.userId());
                assertThat(invitee.get("status")).isEqualTo("PENDING");
            });
            assertThat(get("/users/me/status", friend.accessToken()).text("status")).isEqualTo("IDLE");

            // 2. 거절 — 현황은 응답 전인 초대만 담아 거절한 사람은 빠진다. 다시 거절하면 409다
            assertThat(decline(friend, roomId).status()).isEqualTo(204);
            assertThat(listOf(hostStream.await("INVITE_ROOM_UPDATED"), "invitations")).isEmpty();
            Response declinedAgain = decline(friend, roomId);
            assertThat(declinedAgain.status()).isEqualTo(409);
            assertThat(declinedAgain.text("code")).isEqualTo("RUNNING_INVITATION_NOT_PENDING");
            assertThat(accept(friend, roomId).text("code")).isEqualTo("RUNNING_INVITATION_NOT_PENDING");

            // 3. 다시 초대 — 행이 남아 있어 대기로 되살아나고 횟수는 이어서 센다. 현황에 다시 보인다
            assertThat(invite(host, roomId, friend).status()).isEqualTo(204);
            assertThat(listOf(hostStream.await("INVITE_ROOM_UPDATED"), "invitations"))
                    .extracting(invitee -> invitee.get("status")).containsExactly("PENDING");

            // 4. 수락 — 신청·세션이 생겨 참가자로 옮겨 가고 현황에서 빠진다
            Response accepted = accept(friend, roomId);
            assertThat(accepted.status()).isEqualTo(201);
            assertThat(accepted.number("runningRoomId")).isEqualTo(roomId);
            Map<String, Object> afterAccept = hostStream.await("INVITE_ROOM_UPDATED");
            assertThat(listOf(afterAccept, "players")).extracting(player -> player.get("userId"))
                    .containsExactlyInAnyOrder(host.userId(), friend.userId());
            assertThat(listOf(afterAccept, "invitations")).isEmpty();
            assertThat(get("/users/me/status", friend.accessToken()).text("status")).isEqualTo("WAITING");
            // 이미 들어온 친구를 다시 부르면 "매칭 중"이 아니라 이미 방에 있다고 알린다
            Response again = invite(host, roomId, friend);
            assertThat(again.status()).isEqualTo(409);
            assertThat(again.text("code")).isEqualTo("INVITEE_ALREADY_IN_ROOM");

            // 수락한 사람의 스트림은 초대 방 입장 스냅샷부터 받는다
            try (SseStream friendStream = openMatchStream(friend.accessToken())) {
                Map<String, Object> entrance = friendStream.await("INVITE_ROOM_ENTRANCE");
                assertThat(((Number) entrance.get("runningRoomId")).intValue()).isEqualTo(roomId);
                assertThat(((Number) entrance.get("startsInMs")).longValue()).isPositive();
            }

            // 5. 나가기 — 매칭과 같은 경로다. 참가자에서 빠지고 현황에도 보이지 않는다
            assertThat(post("/match-rooms/leave", null, friend.accessToken()).status()).isEqualTo(204);
            Map<String, Object> afterLeave = hostStream.await("INVITE_ROOM_UPDATED");
            assertThat(listOf(afterLeave, "players")).extracting(player -> player.get("userId"))
                    .containsExactly(host.userId());
            assertThat(listOf(afterLeave, "invitations")).isEmpty();
        }
        assertThat(post("/match-rooms/leave", null, host.accessToken()).status()).isEqualTo(204);
    }

    @Test
    @DisplayName("초대받지 않은 사람은 방 ID로 들어갈 수 없고, 친구가 아닌 사람에게는 보낼 수 없다")
    void strangerIsRejected() {
        // given
        TestUser host = signUpAndOnboard();
        TestUser stranger = signUpAndOnboard();
        int roomId = openRoom(host);

        // when & then - 1. 받은 초대가 없으면 404다
        Response sneaked = accept(stranger, roomId);
        assertThat(sneaked.status()).isEqualTo(404);
        assertThat(sneaked.text("code")).isEqualTo("NOT_FOUND");
        assertThat(get("/users/me/status", stranger.accessToken()).text("status")).isEqualTo("IDLE");

        // 2. 친구가 아니면 409이고 아무에게도 가지 않는다
        Response notFriend = invite(host, roomId, stranger);
        assertThat(notFriend.status()).isEqualTo(409);
        assertThat(notFriend.text("code")).isEqualTo("NOT_FRIEND");
        assertThat(notFriend.text("message")).isEqualTo("친구가 아닙니다.");
        assertThat(accept(stranger, roomId).status()).isEqualTo(404);

        // 3. 참가자가 아닌 사람은 초대할 수 없다 — 방이 있는지도 알려 주지 않는다
        assertThat(invite(stranger, roomId, host).status()).isEqualTo(404);

        assertThat(post("/match-rooms/leave", null, host.accessToken()).status()).isEqualTo(204);
    }

    @Test
    @DisplayName("다른 방에서 매칭 중인 친구는 이유와 함께 거절되고, 초대 뒤에 신청이 생기면 수락이 막힌다")
    void activeApplicationBlocksInvitation() {
        // given
        TestUser host = signUpAndOnboard();
        TestUser busy = signUpAndOnboard();
        TestUser later = signUpAndOnboard();
        befriend(host, busy);
        befriend(host, later);
        int roomId = openRoom(host);
        int busyRoom = openRoom(busy);

        // when - 1. 다른 방에서 매칭 중인 친구는 건너뛰지 않고 이유를 알린다
        Response busyInvite = invite(host, roomId, busy);
        assertThat(busyInvite.status()).isEqualTo(409);
        assertThat(busyInvite.text("code")).isEqualTo("INVITEE_IN_MATCHING");
        assertThat(busyInvite.text("message")).isEqualTo("지금 매칭 중인 친구입니다.");
        assertThat(invite(host, roomId, later).status()).isEqualTo(204);

        // 솔로 방을 연 친구는 시작 전이어도 러닝 중이다 — 솔로는 모집 단계가 없다
        TestUser solo = signUpAndOnboard();
        befriend(host, solo);
        assertThat(post("/running-rooms/solo", Map.of(), solo.accessToken()).status()).isEqualTo(201);
        Response soloInvite = invite(host, roomId, solo);
        assertThat(soloInvite.status()).isEqualTo(409);
        assertThat(soloInvite.text("code")).isEqualTo("INVITEE_RUNNING");
        assertThat(soloInvite.text("message")).isEqualTo("지금 러닝 중인 친구입니다.");
        assertThat(post("/match-rooms/leave", null, solo.accessToken()).status()).isEqualTo(204);

        // 2. 초대받은 뒤 자기 방을 열었다 — 수락이 막히고 초대는 대기로 남는다
        openRoom(later);
        Response blocked = accept(later, roomId);
        assertThat(blocked.status()).isEqualTo(409);
        assertThat(blocked.text("code")).isEqualTo("RUNNING_ALREADY_IN_PROGRESS");

        // 3. 자기 방을 나가면 다시 수락할 수 있다
        assertThat(post("/match-rooms/leave", null, later.accessToken()).status()).isEqualTo(204);
        assertThat(accept(later, roomId).status()).isEqualTo(201);

        assertThat(busyRoom).isNotEqualTo(roomId);
        assertThat(post("/match-rooms/leave", null, busy.accessToken()).status()).isEqualTo(204);
        assertThat(post("/match-rooms/leave", null, later.accessToken()).status()).isEqualTo(204);
        assertThat(post("/match-rooms/leave", null, host.accessToken()).status()).isEqualTo(204);
    }

    @Test
    @DisplayName("혼자인 방에서 만든 사람이 나가면 대기 중이던 초대가 만료돼 수락할 수 없다")
    void lastLeaveExpiresInvitation() {
        // given
        TestUser host = signUpAndOnboard();
        TestUser friend = signUpAndOnboard();
        befriend(host, friend);
        int roomId = openRoom(host);
        assertThat(invite(host, roomId, friend).status()).isEqualTo(204);

        // when -> 방이 취소된다
        assertThat(post("/match-rooms/leave", null, host.accessToken()).status()).isEqualTo(204);

        // then -> 초대 행이 EXPIRED로 저장됐다 — 수락은 방이 닫힌 것, 거절은 대기 초대가 아닌 것으로 막힌다
        Response accepted = accept(friend, roomId);
        assertThat(accepted.status()).isEqualTo(409);
        assertThat(accepted.text("code")).isEqualTo("RUNNING_INVITATION_CLOSED");
        Response declined = decline(friend, roomId);
        assertThat(declined.status()).isEqualTo(409);
        assertThat(declined.text("code")).isEqualTo("RUNNING_INVITATION_NOT_PENDING");
        assertThat(get("/users/me/status", friend.accessToken()).text("status")).isEqualTo("IDLE");
    }

    @Test
    @DisplayName("같은 친구에게 상한(5회)만큼 보내면 그다음은 횟수 초과로 거절된다")
    void inviteCountExceeded() {
        // given
        TestUser host = signUpAndOnboard();
        TestUser friend = signUpAndOnboard();
        befriend(host, friend);
        int roomId = openRoom(host);

        // when - 대기 중에도 다시 보낼 수 있다(독촉) — 실제 설정(invite.max-invite-count=5)까지는 통과한다
        for (int i = 0; i < 5; i++) {
            assertThat(invite(host, roomId, friend).status()).isEqualTo(204);
        }
        Response exceeded = invite(host, roomId, friend);

        // then -> 보낸 횟수는 DB(invite_count)에 쌓인 값으로 판정된다
        assertThat(exceeded.status()).isEqualTo(409);
        assertThat(exceeded.text("code")).isEqualTo("INVITE_COUNT_EXCEEDED");
        assertThat(exceeded.text("message")).isEqualTo("더 이상 이 친구에게 초대를 보낼 수 없습니다.");
        // 상한에 닿아도 초대는 대기로 남아 수락할 수 있다
        assertThat(accept(friend, roomId).status()).isEqualTo(201);

        assertThat(post("/match-rooms/leave", null, friend.accessToken()).status()).isEqualTo(204);
        assertThat(post("/match-rooms/leave", null, host.accessToken()).status()).isEqualTo(204);
    }

    @Test
    @DisplayName("4명이 찬 방에도 초대는 가지만 수락은 막히고, 한 명이 나가면 수락할 수 있다")
    void fullRoomBlocksAcceptUntilSomeoneLeaves() {
        // given -> 방장 + 셋이 들어와 정원 4명이 찼다
        TestUser host = signUpAndOnboard();
        int roomId = openRoom(host);
        List<TestUser> members = List.of(signUpAndOnboard(), signUpAndOnboard(), signUpAndOnboard());
        for (TestUser member : members) {
            befriend(host, member);
            assertThat(invite(host, roomId, member).status()).isEqualTo(204);
            assertThat(accept(member, roomId).status()).isEqualTo(201);
        }
        TestUser waiting = signUpAndOnboard();
        befriend(host, waiting);

        // when - 1. 꽉 찬 방에도 초대는 보낼 수 있다 — 막히는 건 수락뿐이다
        assertThat(invite(host, roomId, waiting).status()).isEqualTo(204);
        Response full = accept(waiting, roomId);

        // then
        assertThat(full.status()).isEqualTo(409);
        assertThat(full.text("code")).isEqualTo("RUNNING_ROOM_FULL");
        assertThat(full.text("message")).isEqualTo("방이 가득 찼습니다.");
        assertThat(get("/users/me/status", waiting.accessToken()).text("status")).isEqualTo("IDLE");

        // 2. 한 명이 나가면 대기로 남아 있던 초대를 수락할 수 있다
        assertThat(post("/match-rooms/leave", null, members.getFirst().accessToken()).status()).isEqualTo(204);
        assertThat(accept(waiting, roomId).status()).isEqualTo(201);

        for (TestUser stayer : List.of(host, members.get(1), members.get(2), waiting)) {
            assertThat(post("/match-rooms/leave", null, stayer.accessToken()).status()).isEqualTo(204);
        }
    }

    @Test
    @DisplayName("두 사람이 같은 친구를 초대하면 행은 따로 저장되고, 수락·나가기·다시 초대는 두 행에 함께 반영된다")
    void twoInvitersShareOneInvitee() {
        // given -> 방 참가자 둘이 같은 친구를 각자 초대한다 — 초대 행은 보낸 사람마다 하나다
        TestUser host = signUpAndOnboard();
        TestUser member = signUpAndOnboard();
        TestUser friend = signUpAndOnboard();
        befriend(host, member);
        befriend(host, friend);
        befriend(member, friend);
        int roomId = openRoom(host);
        assertThat(invite(host, roomId, member).status()).isEqualTo(204);
        assertThat(accept(member, roomId).status()).isEqualTo(201);

        try (SseStream hostStream = openMatchStream(host.accessToken())) {
            hostStream.await("INVITE_ROOM_ENTRANCE");

            // when - 1. 둘 다 보낸다 — 현황은 받는 사람 기준이라 한 줄이다
            assertThat(invite(host, roomId, friend).status()).isEqualTo(204);
            assertThat(invite(member, roomId, friend).status()).isEqualTo(204);
            hostStream.await("INVITE_ROOM_UPDATED");
            assertThat(listOf(hostStream.await("INVITE_ROOM_UPDATED"), "invitations"))
                    .extracting(invitee -> invitee.get("userId")).containsExactly(friend.userId());

            // 2. 수락하고 나간다
            assertThat(accept(friend, roomId).status()).isEqualTo(201);
            hostStream.await("INVITE_ROOM_UPDATED");
            assertThat(post("/match-rooms/leave", null, friend.accessToken()).status()).isEqualTo(204);
            hostStream.await("INVITE_ROOM_UPDATED");

            // 3. 한 사람만 다시 불러도 두 행이 함께 대기로 되살아나 다시 수락할 수 있다
            assertThat(invite(member, roomId, friend).status()).isEqualTo(204);
            hostStream.await("INVITE_ROOM_UPDATED");
            assertThat(accept(friend, roomId).status()).isEqualTo(201);

            // then -> 재수락 때의 이벤트다
            assertThat(listOf(hostStream.await("INVITE_ROOM_UPDATED"), "players"))
                    .extracting(player -> player.get("userId"))
                    .containsExactlyInAnyOrder(host.userId(), member.userId(), friend.userId());
        }
        assertThat(post("/match-rooms/leave", null, friend.accessToken()).status()).isEqualTo(204);
        assertThat(post("/match-rooms/leave", null, member.accessToken()).status()).isEqualTo(204);
        assertThat(post("/match-rooms/leave", null, host.accessToken()).status()).isEqualTo(204);
    }

    @Test
    @DisplayName("초대할 친구가 빠지면 Request 검증 문구로 400이다")
    void missingInviteeIsBadRequest() {
        // given
        TestUser host = signUpAndOnboard();
        int roomId = openRoom(host);

        // when
        Response response = post("/match-rooms/" + roomId + "/invitations",
                Map.of(), host.accessToken());

        // then
        assertThat(response.status()).isEqualTo(400);
        assertThat(response.text("code")).isEqualTo("INVALID_REQUEST");
        assertThat(response.text("message")).isEqualTo("초대할 친구는 필수입니다.");
        assertThat(post("/match-rooms/leave", null, host.accessToken()).status()).isEqualTo(204);
    }
}
