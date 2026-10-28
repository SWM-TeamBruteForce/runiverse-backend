package com.runiverse.running_service.integration_test.relationship;

import com.github.f4b6a3.uuid.UuidCreator;
import com.runiverse.running_service.application.auth.command.signup.SignUpCommand;
import com.runiverse.running_service.application.auth.command.signup.SignUpHandler;
import com.runiverse.running_service.application.relationship.command.accept.AcceptFriendRequestCommand;
import com.runiverse.running_service.application.relationship.command.accept.AcceptFriendRequestHandler;
import com.runiverse.running_service.application.relationship.command.request.RequestFriendCommand;
import com.runiverse.running_service.application.relationship.command.request.RequestFriendHandler;
import com.runiverse.running_service.application.relationship.common.FriendRequestAcceptor;
import com.runiverse.running_service.application.relationship.exception.FriendListOwnerNotFoundException;
import com.runiverse.running_service.application.relationship.exception.InvalidFriendCursorException;
import com.runiverse.running_service.application.relationship.query.friends.GetFriendsHandler;
import com.runiverse.running_service.application.relationship.query.friends.GetFriendsQuery;
import com.runiverse.running_service.application.relationship.query.friends.GetFriendsResult;
import com.runiverse.running_service.application.user.command.onboarding.CompleteOnboardingCommand;
import com.runiverse.running_service.application.user.command.onboarding.CompleteOnboardingHandler;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.relationship.vo.RelationshipType;
import com.runiverse.running_service.domain.user.vo.ProfileImageKey;
import com.runiverse.running_service.integration_test.IntegrationTestSupport;
import com.runiverse.running_service.integration_test.fake.FakeViewUrlGenerator;
import com.runiverse.running_service.integration_test.fake.InMemoryFriendListStore;
import com.runiverse.running_service.integration_test.fake.InMemoryUserRelationshipStore;
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

@DisplayName("친구 목록 조회 통합 테스트")
public class GetFriendsIntegrationTest extends IntegrationTestSupport {

    private static final String PASSWORD = "Password123!";

    private InMemoryUserRelationshipStore relationshipStore;
    private FakeViewUrlGenerator viewUrlGenerator;
    private SignUpHandler signUpHandler;
    private CompleteOnboardingHandler completeOnboardingHandler;
    private RequestFriendHandler requestFriendHandler;
    private AcceptFriendRequestHandler acceptFriendRequestHandler;
    private GetFriendsHandler getFriendsHandler;

    private UUID me;
    private UUID alpha;     // 나에게 요청했다
    private UUID bravo;     // 내가 요청했다
    private UUID charlie;   // 먼저 친구가 됐다
    private UUID delta;     // 나중에 친구가 됐다
    private UUID echo;      // 온보딩 전 — 내가 요청했다

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
        getFriendsHandler = new GetFriendsHandler(
                userStore,                                                                  // LoadUserByIdPort
                new InMemoryFriendListStore(relationshipStore, onboardingStore, userStore), // LoadFriendsPort
                viewUrlGenerator                                                            // GenerateViewUrlPort
        );

        me = onboardedUser("me@runiverse.com", "me_runner");
        alpha = onboardedUser("alpha@runiverse.com", "alpha");
        bravo = onboardedUser("bravo@runiverse.com", "bravo");
        charlie = onboardedUser("charlie@runiverse.com", "charlie");
        delta = onboardedUser("delta@runiverse.com", "delta");
        echo = signUp("echo@runiverse.com");

        // 관계는 실제 요청·수락 유스케이스로 만든다 — 바뀐 순서가 곧 updated_at 순서다
        request(alpha, me);
        request(me, bravo);
        request(me, charlie);
        accept(charlie, me);
        request(delta, me);
        accept(me, delta);
        request(me, echo);
    }

    @Test
    @DisplayName("본인 목록은 받은 요청 → 보낸 요청 → 친구 순이고 친구는 최근에 맺은 순이다")
    void myListIsGroupedAndNewestFirst() {
        // when
        GetFriendsResult result = getFriendsHandler.handle(query(me, me, null, null, 20));

        // then -> 온보딩 전인 echo는 닉네임이 없어 빠진다
        assertThat(result.items()).extracting(GetFriendsResult.Friend::userId)
                .containsExactly(alpha, bravo, delta, charlie);
        assertThat(result.items()).extracting(GetFriendsResult.Friend::relationshipType)
                .containsExactly(RelationshipType.FRIEND_REQUEST_RECEIVED, RelationshipType.FRIEND_REQUEST_SENT,
                        RelationshipType.FRIEND, RelationshipType.FRIEND);
        assertThat(result.nextCursor()).isNull();
    }

    @Test
    @DisplayName("커서로 한 명씩 넘겨도 묶음 경계를 넘어 빠짐없이 한 번씩 이어진다")
    void pagingAcrossGroupBoundaries() {
        // given
        List<UUID> seen = new ArrayList<>();
        String cursor = null;

        // when
        do {
            GetFriendsResult page = getFriendsHandler.handle(query(me, me, null, cursor, 1));
            assertThat(page.items()).hasSize(1);
            seen.add(page.items().get(0).userId());
            cursor = page.nextCursor();
        } while (cursor != null);

        // then -> 마지막 항목에서 nextCursor가 null이라 빈 페이지를 한 번 더 부르지 않는다
        assertThat(seen).containsExactly(alpha, bravo, delta, charlie);
    }

    @Test
    @DisplayName("받은 요청을 수락하면 그 사람은 친구 묶음 맨 앞으로 간다")
    void acceptedRequestMovesToTopOfFriends() {
        // when
        accept(me, alpha);
        GetFriendsResult result = getFriendsHandler.handle(query(me, me, null, null, 20));

        // then
        assertThat(result.items()).extracting(GetFriendsResult.Friend::userId)
                .containsExactly(bravo, alpha, delta, charlie);
    }

    @Test
    @DisplayName("타인 목록은 친구만 담고 관계는 조회자 기준이다")
    void othersListShowsFriendsOnlyFromViewerSide() {
        // given -> bravo는 나에게 요청을 받았지만 charlie·delta와는 관계가 없다
        // when
        GetFriendsResult byBravo = getFriendsHandler.handle(query(bravo, me, null, null, 20));
        GetFriendsResult byDelta = getFriendsHandler.handle(query(delta, me, null, null, 20));

        // then -> 누구에게 요청했고 누가 요청했는지는 본인만 안다
        assertThat(byBravo.items()).extracting(GetFriendsResult.Friend::userId).containsExactly(delta, charlie);
        assertThat(byBravo.items()).extracting(GetFriendsResult.Friend::relationshipType)
                .containsExactly(RelationshipType.NONE, RelationshipType.NONE);
        // 목록에 조회자 본인이 있으면 그 항목은 null이다
        assertThat(byDelta.items()).extracting(GetFriendsResult.Friend::relationshipType)
                .containsExactly(null, RelationshipType.NONE);
    }

    @Test
    @DisplayName("닉네임 부분 일치로 거르고 사진이 있으면 조회 URL을 싣는다")
    void filtersByNicknameAndResolvesImage() {
        // given
        userStore.updateProfileImage(new UserId(charlie), new ProfileImageKey("profile/charlie.png"));

        // when
        GetFriendsResult result = getFriendsHandler.handle(query(me, me, "arl", null, 20));

        // then
        assertThat(result.items()).singleElement().satisfies(friend -> {
            assertThat(friend.userId()).isEqualTo(charlie);
            assertThat(friend.nickname()).isEqualTo("charlie");
            assertThat(friend.profileImageUrl()).isEqualTo(viewUrlGenerator.urlOf("profile/charlie.png"));
        });
    }

    @Test
    @DisplayName("본인 목록에서 받은 요청 묶음 커서를 타인 목록에 쓰면 400이다")
    void myCursorOnOthersListFails() {
        // given
        String myCursor = getFriendsHandler.handle(query(me, me, null, null, 1)).nextCursor();

        // when & then
        assertThatThrownBy(() -> getFriendsHandler.handle(query(bravo, me, null, myCursor, 1)))
                .isInstanceOf(InvalidFriendCursorException.class);
    }

    @Test
    @DisplayName("없는 사용자의 목록은 404다")
    void unknownOwnerIsNotFound() {
        // when & then
        assertThatThrownBy(() -> getFriendsHandler.handle(
                query(me, UuidCreator.getTimeOrderedEpoch(), null, null, 20)))
                .isInstanceOf(FriendListOwnerNotFoundException.class);
    }

    private GetFriendsQuery query(UUID viewerId, UUID ownerId, String q, String cursor, int limit) {
        return new GetFriendsQuery(viewerId, ownerId, q, cursor, limit);
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
