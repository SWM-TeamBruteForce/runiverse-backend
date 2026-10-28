package com.runiverse.running_service.integration_test.relationship;

import com.runiverse.running_service.application.auth.command.signup.SignUpCommand;
import com.runiverse.running_service.application.auth.command.signup.SignUpHandler;
import com.runiverse.running_service.application.relationship.command.accept.AcceptFriendRequestCommand;
import com.runiverse.running_service.application.relationship.command.accept.AcceptFriendRequestHandler;
import com.runiverse.running_service.application.relationship.command.cancel.CancelFriendRequestCommand;
import com.runiverse.running_service.application.relationship.command.cancel.CancelFriendRequestHandler;
import com.runiverse.running_service.application.relationship.command.request.RequestFriendCommand;
import com.runiverse.running_service.application.relationship.command.request.RequestFriendHandler;
import com.runiverse.running_service.application.relationship.common.FriendRequestAcceptor;
import com.runiverse.running_service.application.relationship.common.RelationshipEnder;
import com.runiverse.running_service.application.relationship.exception.InvalidUserSearchCursorException;
import com.runiverse.running_service.application.relationship.query.search.SearchUsersHandler;
import com.runiverse.running_service.application.relationship.query.search.SearchUsersQuery;
import com.runiverse.running_service.application.relationship.query.search.SearchUsersResult;
import com.runiverse.running_service.application.user.command.onboarding.CompleteOnboardingCommand;
import com.runiverse.running_service.application.user.command.onboarding.CompleteOnboardingHandler;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.relationship.vo.RelationshipType;
import com.runiverse.running_service.domain.user.vo.ProfileImageKey;
import com.runiverse.running_service.integration_test.IntegrationTestSupport;
import com.runiverse.running_service.integration_test.fake.FakeViewUrlGenerator;
import com.runiverse.running_service.integration_test.fake.InMemoryUserRelationshipStore;
import com.runiverse.running_service.integration_test.fake.InMemoryUserSearchStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("사용자 검색 통합 테스트")
public class SearchUsersIntegrationTest extends IntegrationTestSupport {

    private static final String PASSWORD = "Password123!";

    private InMemoryUserRelationshipStore relationshipStore;
    private FakeViewUrlGenerator viewUrlGenerator;
    private SignUpHandler signUpHandler;
    private CompleteOnboardingHandler completeOnboardingHandler;
    private RequestFriendHandler requestFriendHandler;
    private AcceptFriendRequestHandler acceptFriendRequestHandler;
    private SearchUsersHandler searchUsersHandler;

    private UUID me;        // runner_me — 검색어에 걸리지만 본인이라 빠진다
    private UUID friend;    // runner_a — 친구가 됐다
    private UUID sent;      // runner_b — 내가 요청했다
    private UUID received;  // runner_c — 나에게 요청했다
    private UUID stranger;  // runner_d — 관계가 없다
    private UUID inner;     // xrunner — 검색어가 가운데에 있다

    @BeforeEach
    void setUp() {
        relationshipStore = new InMemoryUserRelationshipStore();
        viewUrlGenerator = new FakeViewUrlGenerator();
        signUpHandler = newSignUpHandler();
        completeOnboardingHandler = new CompleteOnboardingHandler(
                userStore,        // LoadUserByIdPort
                onboardingStore,  // ExistsOnboardingPort
                onboardingStore,  // CheckNicknameDuplicatePort
                onboardingStore   // SaveOnboardingPort
        );
        FriendRequestAcceptor acceptor = new FriendRequestAcceptor(
                relationshipStore,          // SaveUserRelationshipPort
                (userId, payload) -> { });  // SendUserNotificationPort — 알림 저장은 이 테스트의 관심사가 아니다
        requestFriendHandler = new RequestFriendHandler(
                userStore,          // LoadUserByIdPort
                relationshipStore,  // EnsureUserRelationshipPairPort
                relationshipStore,  // LoadUserRelationshipPort
                relationshipStore,  // SaveUserRelationshipPort
                acceptor,
                (userId, payload) -> { }   // SendUserNotificationPort — 알림 저장은 이 테스트의 관심사가 아니다
        );
        acceptFriendRequestHandler = new AcceptFriendRequestHandler(
                relationshipStore,  // LoadUserRelationshipPort
                acceptor
        );
        searchUsersHandler = new SearchUsersHandler(
                new InMemoryUserSearchStore(onboardingStore, userStore, relationshipStore), // SearchUsersPort
                viewUrlGenerator                                                            // GenerateViewUrlPort
        );

        me = onboardedUser("me@runiverse.com", "runner_me");
        friend = onboardedUser("a@runiverse.com", "runner_a");
        sent = onboardedUser("b@runiverse.com", "runner_b");
        received = onboardedUser("c@runiverse.com", "runner_c");
        stranger = onboardedUser("d@runiverse.com", "runner_d");
        inner = onboardedUser("x@runiverse.com", "xrunner");
        onboardedUser("other@runiverse.com", "walker");
        signUp("noonboard@runiverse.com");

        // 관계는 실제 요청·수락 유스케이스로 만든다
        request(me, friend);
        accept(friend, me);
        request(me, sent);
        request(received, me);
    }

    @Test
    @DisplayName("친구 여부와 상관없이 닉네임 부분 일치로 찾고 본인은 빠지며 닉네임 순이다")
    void searchesAllUsersByNicknameFragment() {
        // when
        SearchUsersResult result = searchUsersHandler.handle(query("runner", null, 20));

        // then -> walker와 온보딩 전 사용자는 걸리지 않는다
        assertThat(result.items()).extracting(SearchUsersResult.User::userId)
                .containsExactly(friend, sent, received, stranger, inner);
        assertThat(result.nextCursor()).isNull();
    }

    @Test
    @DisplayName("관계는 조회자 기준이라 버튼을 그릴 값이 사람마다 다르다")
    void relationshipTypeIsViewerBased() {
        // when
        SearchUsersResult result = searchUsersHandler.handle(query("runner", null, 20));

        // then
        assertThat(result.items()).extracting(SearchUsersResult.User::relationshipType)
                .containsExactly(
                        RelationshipType.FRIEND,
                        RelationshipType.FRIEND_REQUEST_SENT,
                        RelationshipType.FRIEND_REQUEST_RECEIVED,
                        RelationshipType.NONE,
                        RelationshipType.NONE);
    }

    @Test
    @DisplayName("요청을 취소해 NONE으로 돌아간 사람도 관계 없음으로 보인다")
    void cancelledRequestShowsNone() {
        // given -> 행은 남고 type만 NONE이 된다 — 행이 없는 사람과 같게 보여야 다시 요청할 수 있다
        new CancelFriendRequestHandler(relationshipStore, new RelationshipEnder(relationshipStore))
                .handle(new CancelFriendRequestCommand(me, sent));

        // when
        SearchUsersResult result = searchUsersHandler.handle(query("runner_b", null, 20));

        // then
        assertThat(result.items()).singleElement()
                .extracting(SearchUsersResult.User::relationshipType)
                .isEqualTo(RelationshipType.NONE);
    }

    @Test
    @DisplayName("한 명씩 커서로 넘겨도 빠짐없이 한 번씩 이어진다")
    void pagingOneByOne() {
        // given
        List<UUID> seen = new ArrayList<>();
        String cursor = null;

        // when
        do {
            SearchUsersResult page = searchUsersHandler.handle(query("runner", cursor, 1));
            assertThat(page.items()).hasSize(1);
            seen.add(page.items().get(0).userId());
            cursor = page.nextCursor();
        } while (cursor != null);

        // then -> 마지막 항목에서 nextCursor가 null이라 빈 페이지를 한 번 더 부르지 않는다
        assertThat(seen).containsExactly(friend, sent, received, stranger, inner);
    }

    @Test
    @DisplayName("사진이 있으면 조회 URL을 싣는다")
    void resolvesProfileImage() {
        // given
        userStore.updateProfileImage(new UserId(stranger), new ProfileImageKey("profile/d.png"));

        // when
        SearchUsersResult result = searchUsersHandler.handle(query("runner_d", null, 20));

        // then
        assertThat(result.items()).singleElement().satisfies(user -> {
            assertThat(user.userId()).isEqualTo(stranger);
            assertThat(user.nickname()).isEqualTo("runner_d");
            assertThat(user.profileImageUrl()).isEqualTo(viewUrlGenerator.urlOf("profile/d.png"));
        });
    }

    @Test
    @DisplayName("깨진 커서는 400이다")
    void malformedCursorFails() {
        // when & then
        assertThatThrownBy(() -> searchUsersHandler.handle(query("runner", "!!!", 20)))
                .isInstanceOf(InvalidUserSearchCursorException.class);
    }

    private SearchUsersQuery query(String q, String cursor, int limit) {
        return new SearchUsersQuery(me, q, cursor, limit);
    }

    private void request(UUID requesterId, UUID receiverId) {
        requestFriendHandler.handle(new RequestFriendCommand(requesterId, receiverId));
    }

    private void accept(UUID receiverId, UUID requesterId) {
        acceptFriendRequestHandler.handle(new AcceptFriendRequestCommand(receiverId, requesterId));
    }

    private UUID signUp(String email) {
        return signUpHandler.handle(new SignUpCommand(issueVerificationTicket(email), PASSWORD)).userId();
    }

    // 닉네임은 온보딩에서 처음 생긴다
    private UUID onboardedUser(String email, String nickname) {
        UUID userId = signUp(email);
        completeOnboardingHandler.handle(new CompleteOnboardingCommand(
                userId, nickname, "MALE", LocalDate.of(1999, 5, 20),
                330, new BigDecimal("70.5"), new BigDecimal("175.0")));
        return userId;
    }
}
