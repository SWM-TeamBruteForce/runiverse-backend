package com.runiverse.e2e.relationship;

import com.runiverse.e2e.E2eTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("배포 이미지 대상 친구 요청 E2E 테스트")
class FriendRequestE2eTest extends E2eTestSupport {

    // 동시 요청이 실제로 겹칠 기회를 늘리려고 여러 쌍으로 반복한다
    private static final int CONCURRENT_PAIRS = 5;

    @Test
    @DisplayName("요청하면 201로 보낸 요청이 되고, 받은 쪽이 수락하면 둘은 친구가 된다")
    void requestThenAccept() {
        // given
        TestUser requester = signUpAndOnboard();
        TestUser receiver = signUpAndOnboard();

        // when - 처음 맺는 쌍이라 행이 없는 상태에서 ON CONFLICT로 행을 확보한 뒤 잠가 읽는다
        Response requested = post(friendRequestPath(receiver), null, requester.accessToken());

        // then
        assertThat(requested.status()).isEqualTo(201);
        assertThat(requested.text("relationshipType")).isEqualTo("FRIEND_REQUEST_SENT");
        // 보낸 사람은 자기 요청을 수락할 수 없다
        assertThat(put(friendRequestPath(receiver), requester.accessToken()).status()).isEqualTo(404);

        // when - 받은 쪽이 수락한다
        Response accepted = put(friendRequestPath(requester), receiver.accessToken());

        // then
        assertThat(accepted.status()).isEqualTo(200);
        assertThat(accepted.text("relationshipType")).isEqualTo("FRIEND");
        // 수락은 멱등이다 — 다시 보내도 같은 응답이다
        Response acceptedAgain = put(friendRequestPath(requester), receiver.accessToken());
        assertThat(acceptedAgain.status()).isEqualTo(200);
        assertThat(acceptedAgain.text("relationshipType")).isEqualTo("FRIEND");
        // 친구가 된 뒤에는 어느 쪽에서 요청해도 409다
        assertThat(post(friendRequestPath(receiver), null, requester.accessToken()).status()).isEqualTo(409);
        Response conflict = post(friendRequestPath(requester), null, receiver.accessToken());
        assertThat(conflict.status()).isEqualTo(409);
        assertThat(conflict.text("code")).isEqualTo("FRIEND_REQUEST_ALREADY_EXISTS");
    }

    @Test
    @DisplayName("받은 쪽이 거꾸로 요청하면 수락 없이 200으로 바로 친구가 된다")
    void mutualRequestBecomesFriend() {
        // given
        TestUser requester = signUpAndOnboard();
        TestUser receiver = signUpAndOnboard();
        post(friendRequestPath(receiver), null, requester.accessToken());

        // when
        Response mutual = post(friendRequestPath(requester), null, receiver.accessToken());

        // then
        assertThat(mutual.status()).isEqualTo(200);
        assertThat(mutual.text("relationshipType")).isEqualTo("FRIEND");
        // 요청했던 쪽에서 봐도 친구다 — 수락이 멱등이라 상태를 바꾸지 않고 확인만 한다
        assertThat(put(friendRequestPath(receiver), requester.accessToken()).text("relationshipType"))
                .isEqualTo("FRIEND");
    }

    @Test
    @DisplayName("처음 맺는 두 사람이 동시에 서로 요청해도 500 없이 친구가 된다")
    void concurrentMutualRequestsBecomeFriend() {
        for (int i = 0; i < CONCURRENT_PAIRS; i++) {
            // given
            TestUser first = signUpAndOnboard();
            TestUser second = signUpAndOnboard();
            CountDownLatch start = new CountDownLatch(1);

            // when - 행이 없는 쌍에 양쪽 INSERT가 겹친다. UNIQUE 위반이 새면 500이다
            CompletableFuture<Response> fromFirst = CompletableFuture.supplyAsync(() -> {
                await(start);
                return post(friendRequestPath(second), null, first.accessToken());
            });
            CompletableFuture<Response> fromSecond = CompletableFuture.supplyAsync(() -> {
                await(start);
                return post(friendRequestPath(first), null, second.accessToken());
            });
            start.countDown();
            List<Response> responses = List.of(fromFirst.join(), fromSecond.join());

            // then - 먼저 잠근 쪽은 새 요청(201), 뒤에 온 쪽은 그 요청을 받아 수락(200)한다
            assertThat(responses).extracting(Response::status).containsExactlyInAnyOrder(201, 200);
            assertThat(responses).extracting(response -> response.text("relationshipType"))
                    .containsExactlyInAnyOrder("FRIEND_REQUEST_SENT", "FRIEND");
            // 양쪽 행이 모두 친구다
            assertThat(put(friendRequestPath(second), first.accessToken()).text("relationshipType")).isEqualTo("FRIEND");
            assertThat(put(friendRequestPath(first), second.accessToken()).text("relationshipType")).isEqualTo("FRIEND");
        }
    }

    @Test
    @DisplayName("같은 사람에게 다시 요청하면 409이고, 자기 자신에게는 400이다")
    void duplicateAndSelfRequestsAreRejected() {
        // given
        TestUser requester = signUpAndOnboard();
        TestUser receiver = signUpAndOnboard();
        post(friendRequestPath(receiver), null, requester.accessToken());

        // when & then
        Response duplicate = post(friendRequestPath(receiver), null, requester.accessToken());
        assertThat(duplicate.status()).isEqualTo(409);
        assertThat(duplicate.text("code")).isEqualTo("FRIEND_REQUEST_ALREADY_EXISTS");

        Response self = post(friendRequestPath(requester), null, requester.accessToken());
        assertThat(self.status()).isEqualTo(400);
        assertThat(self.text("code")).isEqualTo("CANNOT_FRIEND_SELF");
    }

    @Test
    @DisplayName("없는 사용자에게 요청하거나 받은 적 없는 요청을 수락하면 404다")
    void unknownTargetIsNotFound() {
        // given - UUIDv7 형식이지만 가입한 적 없는 사용자다
        TestUser user = signUpAndOnboard();
        String unknownUserId = "01900000-0000-7000-8000-" + UUID.randomUUID().toString().substring(24);
        TestUser stranger = signUpAndOnboard();

        // when & then
        Response requested = post("/users/" + unknownUserId + "/friend-request", null, user.accessToken());
        assertThat(requested.status()).isEqualTo(404);
        assertThat(requested.text("code")).isEqualTo("NOT_FOUND");
        assertThat(put(friendRequestPath(stranger), user.accessToken()).status()).isEqualTo(404);
    }

    @Test
    @DisplayName("친구 요청과 수락은 토큰이 필요하다")
    void friendRequestRequiresToken() {
        // given
        TestUser user = signUpAndOnboard();

        // when & then - /users/** 공개 경로가 넓어지면 토큰 없이 통과한다
        assertThat(post(friendRequestPath(user), null, null).status()).isEqualTo(401);
        assertThat(put(friendRequestPath(user), null).status()).isEqualTo(401);
    }

    private static String friendRequestPath(TestUser target) {
        return "/users/" + target.userId() + "/friend-request";
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
