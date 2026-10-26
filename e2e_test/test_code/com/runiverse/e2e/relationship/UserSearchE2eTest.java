package com.runiverse.e2e.relationship;

import com.runiverse.e2e.E2eTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("배포 이미지 대상 사용자 검색 E2E 테스트")
class UserSearchE2eTest extends E2eTestSupport {

    // DB를 비울 수 없어 다른 테스트의 사용자도 검색된다 — 이 테스트의 사용자만 공통 조각을 갖게 닉네임을 바꾼다
    private String tag;
    private TestUser me;
    private TestUser friend;     // 친구가 됐다
    private TestUser sent;       // 내가 요청했다
    private TestUser received;   // 나에게 요청했다
    private TestUser stranger;   // 관계가 없다
    private TestUser inner;      // 조각이 닉네임 가운데에 있다

    @BeforeEach
    void setUp() {
        // runner + 임의 8자에서 임의 부분만 빌린다 — 소문자 영문·숫자라 콜레이션과 상관없이 순서가 정해진다
        tag = "srch" + uniqueNickname().substring("runner".length());
        me = withNickname(signUpAndOnboard(), tag + "m");
        friend = withNickname(signUpAndOnboard(), tag + "a");
        sent = withNickname(signUpAndOnboard(), tag + "b");
        received = withNickname(signUpAndOnboard(), tag + "c");
        stranger = withNickname(signUpAndOnboard(), tag + "d");
        inner = withNickname(signUpAndOnboard(), "x" + tag + "z");

        post(friendRequestPath(friend), null, me.accessToken());
        put(friendRequestPath(me), friend.accessToken());
        post(friendRequestPath(sent), null, me.accessToken());
        post(friendRequestPath(me), null, received.accessToken());
    }

    @Test
    @DisplayName("친구 여부와 상관없이 부분 일치로 찾고 본인은 빠지며 닉네임 순이고 관계는 조회자 기준이다")
    void searchesAllUsersWithViewerRelationship() {
        // when - user_onboardings에서 출발해 관계를 LEFT JOIN하는 JPQL이 실제 DB에서 돈다
        Response response = get(searchPath("q=" + encode(tag)), me.accessToken());

        // then
        assertThat(response.status()).isEqualTo(200);
        assertThat(userIds(response)).containsExactly(
                friend.userId(), sent.userId(), received.userId(), stranger.userId(), inner.userId());
        assertThat(response.objects("items")).extracting(item -> item.get("relationshipType"))
                .containsExactly("FRIEND", "FRIEND_REQUEST_SENT", "FRIEND_REQUEST_RECEIVED", "NONE", "NONE");
        assertThat(response.objects("items").get(0))
                .containsEntry("nickname", tag + "a")
                .containsEntry("profileImageUrl", null);
        assertThat(response.body()).containsEntry("nextCursor", null);
    }

    @Test
    @DisplayName("한 명씩 커서로 넘겨도 빠짐없이 한 번씩 이어진다")
    void pagingOneByOne() {
        // given
        List<String> seen = new ArrayList<>();
        String cursor = null;

        // when - 커서의 닉네임 비교와 ORDER BY가 같은 콜레이션을 따라야 빠지거나 겹치지 않는다
        do {
            String query = "q=" + encode(tag) + "&limit=1" + (cursor == null ? "" : "&cursor=" + cursor);
            Response page = get(searchPath(query), me.accessToken());
            assertThat(page.status()).isEqualTo(200);
            assertThat(page.objects("items")).hasSize(1);
            seen.add((String) page.objects("items").get(0).get("userId"));
            cursor = page.text("nextCursor");
        } while (cursor != null && seen.size() < 10);

        // then
        assertThat(seen).containsExactly(
                friend.userId(), sent.userId(), received.userId(), stranger.userId(), inner.userId());
    }

    @Test
    @DisplayName("%는 와일드카드가 아니라 글자로 찾는다")
    void percentIsLiteral() {
        // when
        Response response = get(searchPath("q=" + encode("%")), me.accessToken());

        // then - ESCAPE가 빠지면 %가 모든 사용자에 걸린다. 닉네임에는 %를 쓸 수 없어 아무도 없다
        assertThat(response.status()).isEqualTo(200);
        assertThat(response.objects("items")).isEmpty();
    }

    @Test
    @DisplayName("검색어가 없거나 비었거나 깨진 커서면 400이고, 토큰이 없으면 401이다")
    void invalidRequests() {
        // when
        Response missingQuery = get(searchPath(""), me.accessToken());
        Response blankQuery = get(searchPath("q=" + encode("   ")), me.accessToken());
        Response broken = get(searchPath("q=" + encode(tag) + "&cursor=!!!"), me.accessToken());
        Response noToken = get(searchPath("q=" + encode(tag)), null);

        // then - 검색어 누락이 처리되지 않은 예외로 500이 되지 않아야 한다
        for (Response response : List.of(missingQuery, blankQuery, broken)) {
            assertThat(response.status()).isEqualTo(400);
            assertThat(response.text("code")).isEqualTo("INVALID_REQUEST");
        }
        assertThat(noToken.status()).isEqualTo(401);
    }

    private TestUser withNickname(TestUser user, String nickname) {
        Response changed = patch("/users/me/nickname", Map.of("nickname", nickname), user.accessToken());
        assertThat(changed.status()).isEqualTo(200);
        return new TestUser(user.userId(), user.email(), user.password(), nickname, user.accessToken());
    }

    private static List<String> userIds(Response response) {
        return response.objects("items").stream()
                .map(item -> (String) item.get("userId"))
                .toList();
    }

    private static String searchPath(String query) {
        return "/users/search" + (query.isEmpty() ? "" : "?" + query);
    }

    private static String friendRequestPath(TestUser target) {
        return "/users/" + target.userId() + "/friend-request";
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
