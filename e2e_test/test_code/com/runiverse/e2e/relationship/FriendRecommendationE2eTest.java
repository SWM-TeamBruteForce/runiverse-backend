package com.runiverse.e2e.relationship;

import com.runiverse.e2e.E2eTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("배포 이미지 대상 추천 친구 E2E 테스트")
class FriendRecommendationE2eTest extends E2eTestSupport {

    private static final String RECOMMENDATIONS_PATH = "/users/me/friend-recommendations";

    @Test
    @DisplayName("가장 최근 매칭 방에서 함께 뛴 사람이 먼저, 페이스가 비슷한 사람이 차이순으로 뒤를 채운다")
    void ranTogetherFromLatestMatchThenSimilarPace() {
        // given - 페이스는 다른 테스트의 사용자(330)와 섞이지 않는 1500 근처를 쓴다 (허용 차이 20)
        TestUser me = signUpAndOnboard(1500);
        TestUser oldMatchRunner = signUpAndOnboard(1000);
        TestUser latestRunner = signUpAndOnboard(1510);         // 페이스도 비슷하지만 함께 뛴 사람으로 한 번만 나온다
        TestUser leftBeforeStart = signUpAndOnboard(1100);      // 같은 방이지만 시작 전에 나갔다
        TestUser inviteRunner = signUpAndOnboard(1200);
        TestUser closer = signUpAndOnboard(1495);
        TestUser close = signUpAndOnboard(1519);
        TestUser far = signUpAndOnboard(1521);
        TestUser friend = signUpAndOnboard(1501);

        // 시작 시각이 이를수록 오래된 방이다. 가장 최근 러닝은 솔로·초대라 그 앞의 매칭 방을 봐야 한다
        seedFinishedRoom("MATCH", 30, Map.of(me, "COMPLETED", oldMatchRunner, "COMPLETED"));
        seedFinishedRoom("MATCH", 20, Map.of(me, "COMPLETED",
                latestRunner, "RUNNING_LEFT_NO_PENALTY", leftBeforeStart, "MATCHED_LEFT_NO_PENALTY"));
        seedFinishedRoom("INVITE", 10, Map.of(me, "COMPLETED", inviteRunner, "COMPLETED"));
        seedFinishedRoom("SOLO", 5, Map.of(me, "COMPLETED"));
        post(friendRequestPath(friend), null, me.accessToken());
        put(friendRequestPath(me), friend.accessToken());

        // when
        Response response = get(RECOMMENDATIONS_PATH, me.accessToken());

        // then
        assertThat(response.status()).isEqualTo(200);
        assertThat(response.objects("recommendations")).extracting(item -> item.get("userId"))
                .containsExactly(latestRunner.userId(), closer.userId(), close.userId());
        assertThat(response.objects("recommendations")).extracting(item -> item.get("reason"))
                .containsExactly("RAN_TOGETHER", "SIMILAR_PACE", "SIMILAR_PACE");
        assertThat(response.objects("recommendations").get(0))
                .containsEntry("nickname", latestRunner.nickname())
                .containsEntry("profileImageUrl", null)
                .doesNotContainKey("relationshipType");
        assertThat(response.objects("recommendations")).extracting(item -> item.get("userId"))
                .doesNotContain(far.userId(), friend.userId(), me.userId());
    }

    @Test
    @DisplayName("요청을 보낸 사람과 받은 사람은 함께 뛰었어도 추천에서 빠진다")
    void pendingRequestsAreExcluded() {
        // given
        TestUser me = signUpAndOnboard(1600);
        TestUser requestedByMe = signUpAndOnboard(1601);
        TestUser requestedMe = signUpAndOnboard(1602);
        seedFinishedRoom("MATCH", 5, Map.of(me, "COMPLETED", requestedByMe, "COMPLETED", requestedMe, "COMPLETED"));
        post(friendRequestPath(requestedByMe), null, me.accessToken());
        post(friendRequestPath(me), null, requestedMe.accessToken());

        // when
        Response response = get(RECOMMENDATIONS_PATH, me.accessToken());

        // then - 결과는 모두 친구 요청을 보낼 수 있는 사람이다
        assertThat(response.status()).isEqualTo(200);
        assertThat(response.objects("recommendations")).isEmpty();
    }

    @Test
    @DisplayName("매칭 러닝도 없고 페이스가 비슷한 사람도 없으면 빈 배열이고, 토큰이 없으면 401이다")
    void emptyAndUnauthorized() {
        // given
        TestUser me = signUpAndOnboard(1700);

        // when
        Response response = get(RECOMMENDATIONS_PATH, me.accessToken());
        Response noToken = get(RECOMMENDATIONS_PATH, null);

        // then
        assertThat(response.status()).isEqualTo(200);
        assertThat(response.objects("recommendations")).isEmpty();
        assertThat(noToken.status()).isEqualTo(401);
    }

    // 끝난 방 하나와 참가자들의 신청·배정 행을 심는다. hoursAgo가 클수록 오래된 방이다
    private void seedFinishedRoom(String type, int hoursAgo, Map<TestUser, String> statusByUser) {
        Map<TestUser, String> ordered = new LinkedHashMap<>(statusByUser);
        String players = ordered.entrySet().stream()
                .map(entry -> String.format(
                        "('%s'::uuid, '%s', now() - interval '%d hours', 3000, 330, 4, now(), now(), now())",
                        entry.getKey().userId(), entry.getValue(), hoursAgo))
                .collect(Collectors.joining(",\n       "));
        int maxPlayerCount = type.equals("SOLO") ? 1 : 4;
        executeSql(String.format("""
                WITH room AS (
                    INSERT INTO running_rooms (type, status, start_at, close_at, target_distance, avg_pace,
                                               max_player_count, current_player_count, created_at, updated_at)
                    VALUES ('%s', 'FINISHED', now() - interval '%d hours', now() - interval '%d hours' + interval '30 minutes',
                            3000, 330, %d, %d, now(), now())
                    RETURNING running_room_id
                ), players AS (
                    INSERT INTO running_players (user_id, status, start_at, target_distance, avg_pace,
                                                 desired_player_count, created_at, updated_at, deleted_at)
                    VALUES %s
                    RETURNING running_player_id, user_id
                )
                INSERT INTO running_room_sessions (running_room_id, user_id, running_player_id, leave_count,
                                                   is_connected, created_at, updated_at)
                SELECT room.running_room_id, players.user_id, players.running_player_id, 0, false, now(), now()
                FROM room, players;
                """, type, hoursAgo, hoursAgo, maxPlayerCount, ordered.size(), players));
    }

    private static String friendRequestPath(TestUser target) {
        return "/users/" + target.userId() + "/friend-request";
    }
}
