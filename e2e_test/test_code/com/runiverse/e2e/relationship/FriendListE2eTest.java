package com.runiverse.e2e.relationship;

import com.runiverse.e2e.E2eTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("배포 이미지 대상 친구 목록 E2E 테스트")
class FriendListE2eTest extends E2eTestSupport {

    private TestUser me;
    private TestUser alpha;     // 나에게 요청했다
    private TestUser bravo;     // 내가 요청했다
    private TestUser charlie;   // 먼저 친구가 됐다
    private TestUser delta;     // 나중에 친구가 됐다

    @BeforeEach
    void setUp() {
        me = signUpAndOnboard();
        alpha = signUpAndOnboard();
        bravo = signUpAndOnboard();
        charlie = signUpAndOnboard();
        delta = signUpAndOnboard();

        // 바뀐 순서가 곧 updated_at 순서다
        post(friendRequestPath(me), null, alpha.accessToken());
        post(friendRequestPath(bravo), null, me.accessToken());
        post(friendRequestPath(charlie), null, me.accessToken());
        put(friendRequestPath(me), charlie.accessToken());
        post(friendRequestPath(me), null, delta.accessToken());
        put(friendRequestPath(delta), me.accessToken());
    }

    @Test
    @DisplayName("본인 목록은 받은 요청 → 보낸 요청 → 친구 순이고 친구는 최근에 맺은 순이다")
    void myListIsGroupedAndNewestFirst() {
        // when - 묶음마다 idx_user_relationship_list를 타는 JPQL이 실제 DB에서 돈다
        Response response = get(friendsPath(me, ""), me.accessToken());

        // then
        assertThat(response.status()).isEqualTo(200);
        assertThat(userIds(response)).containsExactly(alpha.userId(), bravo.userId(), delta.userId(), charlie.userId());
        assertThat(response.objects("items")).extracting(item -> item.get("relationshipType"))
                .containsExactly("FRIEND_REQUEST_RECEIVED", "FRIEND_REQUEST_SENT", "FRIEND", "FRIEND");
        assertThat(response.objects("items").get(0))
                .containsEntry("nickname", alpha.nickname())
                .containsEntry("profileImageUrl", null);
        assertThat(response.body()).containsEntry("nextCursor", null);
    }

    @Test
    @DisplayName("한 명씩 커서로 넘겨도 묶음 경계를 넘어 빠짐없이 한 번씩 이어진다")
    void pagingAcrossGroupBoundaries() {
        // given
        List<String> seen = new ArrayList<>();
        String cursor = null;

        // when - 커서의 마이크로초 시각과 uuid 비교가 실제 DB에서 같은 위치를 가리켜야 한다
        do {
            String query = "limit=1" + (cursor == null ? "" : "&cursor=" + cursor);
            Response page = get(friendsPath(me, query), me.accessToken());
            assertThat(page.status()).isEqualTo(200);
            assertThat(page.objects("items")).hasSize(1);
            seen.add((String) page.objects("items").get(0).get("userId"));
            cursor = page.text("nextCursor");
        } while (cursor != null && seen.size() < 10);

        // then
        assertThat(seen).containsExactly(alpha.userId(), bravo.userId(), delta.userId(), charlie.userId());
    }

    @Test
    @DisplayName("받은 요청을 수락하면 그 사람은 친구 묶음 맨 앞으로 간다")
    void acceptedRequestMovesToTopOfFriends() {
        // when
        put(friendRequestPath(alpha), me.accessToken());
        Response response = get(friendsPath(me, ""), me.accessToken());

        // then
        assertThat(userIds(response)).containsExactly(bravo.userId(), alpha.userId(), delta.userId(), charlie.userId());
    }

    @Test
    @DisplayName("타인 목록은 친구만 담고 관계는 조회자 기준이며 목록 속 조회자 본인은 null이다")
    void othersListShowsFriendsOnlyFromViewerSide() {
        // when
        Response byBravo = get(friendsPath(me, ""), bravo.accessToken());
        Response byDelta = get(friendsPath(me, ""), delta.accessToken());

        // then - 누구에게 요청했고 누가 요청했는지는 본인만 안다
        assertThat(byBravo.status()).isEqualTo(200);
        assertThat(userIds(byBravo)).containsExactly(delta.userId(), charlie.userId());
        assertThat(byBravo.objects("items")).extracting(item -> item.get("relationshipType"))
                .containsExactly("NONE", "NONE");
        // 조회자 행을 LEFT JOIN으로 붙인다 — 행이 없는 본인 항목은 null이다
        assertThat(byDelta.objects("items")).extracting(item -> item.get("relationshipType"))
                .containsExactly(null, "NONE");
    }

    @Test
    @DisplayName("이름 검색은 부분 일치이고 %는 와일드카드가 아니라 글자로 찾는다")
    void searchByNicknameFragment() {
        // given - 닉네임은 runner + 임의 8자라 뒷부분으로 한 명만 걸린다
        String fragment = charlie.nickname().substring(charlie.nickname().length() - 5);

        // when
        Response matched = get(friendsPath(me, "q=" + encode(fragment)), me.accessToken());
        Response percent = get(friendsPath(me, "q=" + encode("%")), me.accessToken());

        // then - ESCAPE가 빠지면 %가 모든 친구에 걸린다
        assertThat(userIds(matched)).containsExactly(charlie.userId());
        assertThat(percent.status()).isEqualTo(200);
        assertThat(percent.objects("items")).isEmpty();
    }

    @Test
    @DisplayName("깨진 커서와 다른 목록의 커서는 400이고, 없는 사용자는 404, 토큰이 없으면 401이다")
    void invalidRequests() {
        // given - 본인 목록의 첫 페이지 커서는 받은 요청 묶음을 가리킨다
        String myCursor = get(friendsPath(me, "limit=1"), me.accessToken()).text("nextCursor");

        // when
        Response broken = get(friendsPath(me, "cursor=broken"), me.accessToken());
        Response foreignCursor = get(friendsPath(me, "cursor=" + myCursor), bravo.accessToken());
        Response unknown = get("/users/" + UUID.fromString("01a0f2d4-6180-7000-8000-000000000000") + "/friends",
                me.accessToken());
        Response zeroLimit = get(friendsPath(me, "limit=0"), me.accessToken());
        Response noToken = get(friendsPath(me, ""), null);

        // then
        assertThat(broken.status()).isEqualTo(400);
        assertThat(broken.text("code")).isEqualTo("INVALID_REQUEST");
        assertThat(foreignCursor.status()).isEqualTo(400);
        assertThat(foreignCursor.text("code")).isEqualTo("INVALID_REQUEST");
        assertThat(unknown.status()).isEqualTo(404);
        assertThat(unknown.text("code")).isEqualTo("NOT_FOUND");
        assertThat(zeroLimit.status()).isEqualTo(400);
        assertThat(noToken.status()).isEqualTo(401);
    }

    private static List<String> userIds(Response response) {
        return response.objects("items").stream()
                .map(item -> (String) item.get("userId"))
                .toList();
    }

    private static String friendsPath(TestUser owner, String query) {
        return "/users/" + owner.userId() + "/friends" + (query.isEmpty() ? "" : "?" + query);
    }

    private static String friendRequestPath(TestUser target) {
        return "/users/" + target.userId() + "/friend-request";
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
