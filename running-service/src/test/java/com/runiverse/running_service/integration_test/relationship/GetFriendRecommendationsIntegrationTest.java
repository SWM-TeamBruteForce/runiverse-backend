package com.runiverse.running_service.integration_test.relationship;

import com.runiverse.running_service.application.auth.command.signup.SignUpCommand;
import com.runiverse.running_service.application.auth.command.signup.SignUpHandler;
import com.runiverse.running_service.application.relationship.command.request.RequestFriendCommand;
import com.runiverse.running_service.application.relationship.command.request.RequestFriendHandler;
import com.runiverse.running_service.application.relationship.common.FriendRequestAcceptor;
import com.runiverse.running_service.application.relationship.query.recommendation.FriendRecommendationProperties;
import com.runiverse.running_service.application.relationship.query.recommendation.GetFriendRecommendationsHandler;
import com.runiverse.running_service.application.relationship.query.recommendation.GetFriendRecommendationsQuery;
import com.runiverse.running_service.application.relationship.query.recommendation.GetFriendRecommendationsResult;
import com.runiverse.running_service.application.relationship.query.recommendation.RecommendationReason;
import com.runiverse.running_service.application.user.command.onboarding.CompleteOnboardingCommand;
import com.runiverse.running_service.application.user.command.onboarding.CompleteOnboardingHandler;
import com.runiverse.running_service.integration_test.IntegrationTestSupport;
import com.runiverse.running_service.integration_test.fake.FakeViewUrlGenerator;
import com.runiverse.running_service.integration_test.fake.InMemoryFriendRecommendationStore;
import com.runiverse.running_service.integration_test.fake.InMemoryUserRelationshipStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("추천 친구 조회 통합 테스트")
public class GetFriendRecommendationsIntegrationTest extends IntegrationTestSupport {

    private static final String PASSWORD = "Password123!";
    private static final int MAX_COUNT = 30;
    private static final int TOLERANCE = 20;

    private InMemoryUserRelationshipStore relationshipStore;
    private InMemoryFriendRecommendationStore recommendationStore;
    private SignUpHandler signUpHandler;
    private CompleteOnboardingHandler completeOnboardingHandler;
    private RequestFriendHandler requestFriendHandler;
    private GetFriendRecommendationsHandler handler;

    private int sequence;

    @BeforeEach
    void setUp() {
        relationshipStore = new InMemoryUserRelationshipStore();
        recommendationStore = new InMemoryFriendRecommendationStore(relationshipStore, onboardingStore, userStore);
        signUpHandler = newSignUpHandler();
        completeOnboardingHandler = new CompleteOnboardingHandler(
                userStore,        // LoadUserByIdPort
                onboardingStore,  // ExistsOnboardingPort
                onboardingStore,  // CheckNicknameDuplicatePort
                onboardingStore   // SaveOnboardingPort
        );
        requestFriendHandler = new RequestFriendHandler(
                userStore,          // LoadUserByIdPort
                relationshipStore,  // EnsureUserRelationshipPairPort
                relationshipStore,  // LoadUserRelationshipPort
                relationshipStore,  // SaveUserRelationshipPort
                new FriendRequestAcceptor(relationshipStore)
        );
        handler = new GetFriendRecommendationsHandler(
                recommendationStore,         // LoadFriendRecommendationsPort
                onboardingStore,             // LoadUserAvgPacePort
                new FakeViewUrlGenerator(),  // GenerateViewUrlPort
                new FriendRecommendationProperties(MAX_COUNT, TOLERANCE));
    }

    @Test
    @DisplayName("함께 뛴 사람이 먼저 오고, 페이스 범위 안의 사람이 차이가 작은 순으로 뒤를 채운다")
    void ranTogetherFirstThenClosestPace() {
        // given
        UUID me = runner(330);
        UUID runner = runner(600);      // 페이스는 멀어도 함께 뛰었다
        UUID close = runner(335);
        UUID closer = runner(331);
        UUID edge = runner(350);        // 경계값은 포함한다
        UUID far = runner(351);
        recommendationStore.seedLatestMatchRunners(me, runner);

        // when
        GetFriendRecommendationsResult result = handler.handle(new GetFriendRecommendationsQuery(me));

        // then
        assertThat(result.recommendations()).extracting(GetFriendRecommendationsResult.Recommendation::userId)
                .containsExactly(runner, closer, close, edge)
                .doesNotContain(far, me);
        assertThat(result.recommendations()).extracting(GetFriendRecommendationsResult.Recommendation::reason)
                .containsExactly(RecommendationReason.RAN_TOGETHER, RecommendationReason.SIMILAR_PACE,
                        RecommendationReason.SIMILAR_PACE, RecommendationReason.SIMILAR_PACE);
    }

    @Test
    @DisplayName("함께 뛰었고 페이스도 비슷한 사람은 함께 뛴 사람으로 한 번만 나온다")
    void bothBranchesAppearOnceAsRanTogether() {
        // given
        UUID me = runner(330);
        UUID both = runner(332);
        recommendationStore.seedLatestMatchRunners(me, both);

        // when
        GetFriendRecommendationsResult result = handler.handle(new GetFriendRecommendationsQuery(me));

        // then
        assertThat(result.recommendations()).singleElement().satisfies(recommendation -> {
            assertThat(recommendation.userId()).isEqualTo(both);
            assertThat(recommendation.reason()).isEqualTo(RecommendationReason.RAN_TOGETHER);
        });
    }

    @Test
    @DisplayName("요청을 주고받았거나 친구인 사람과 온보딩 전인 사람은 어느 갈래에서도 빠진다")
    void unavailableUsersAreExcluded() {
        // given
        UUID me = runner(330);
        UUID requestedByMe = runner(331);
        UUID requestedMe = runner(332);
        UUID friendRunner = runner(333);
        UUID notOnboarded = signUp();
        UUID available = runner(334);
        request(me, requestedByMe);
        request(requestedMe, me);
        request(me, friendRunner);
        request(friendRunner, me);      // 서로 요청해 친구가 된다
        recommendationStore.seedLatestMatchRunners(me, friendRunner, notOnboarded);

        // when
        GetFriendRecommendationsResult result = handler.handle(new GetFriendRecommendationsQuery(me));

        // then -> 결과는 모두 친구 요청을 보낼 수 있는 사람이다
        assertThat(result.recommendations()).extracting(GetFriendRecommendationsResult.Recommendation::userId)
                .containsExactly(available);
    }

    @Test
    @DisplayName("총 인원을 넘게 후보가 있어도 상한까지만 내린다")
    void capsAtMaxCount() {
        // given
        UUID me = runner(330);
        IntStream.range(0, MAX_COUNT + 5).forEach(i -> runner(330));

        // when & then
        assertThat(handler.handle(new GetFriendRecommendationsQuery(me)).recommendations()).hasSize(MAX_COUNT);
    }

    @Test
    @DisplayName("온보딩 전이면 페이스 갈래는 비고 함께 뛴 사람만 나온다")
    void notOnboardedGetsRanTogetherOnly() {
        // given
        UUID me = signUp();
        UUID runner = runner(330);
        runner(330);
        recommendationStore.seedLatestMatchRunners(me, runner);

        // when
        GetFriendRecommendationsResult result = handler.handle(new GetFriendRecommendationsQuery(me));

        // then
        assertThat(result.recommendations()).extracting(GetFriendRecommendationsResult.Recommendation::userId)
                .containsExactly(runner);
    }

    private void request(UUID requesterId, UUID receiverId) {
        requestFriendHandler.handle(new RequestFriendCommand(requesterId, receiverId));
    }

    private UUID signUp() {
        String email = "runner" + (++sequence) + "@runiverse.com";
        return signUpHandler.handle(new SignUpCommand(issueVerificationTicket(email), PASSWORD)).userId();
    }

    // 평균 페이스는 온보딩 입력이 초기값이다
    private UUID runner(int avgPaceSecondsPerKm) {
        UUID userId = signUp();
        completeOnboardingHandler.handle(new CompleteOnboardingCommand(
                userId, "runner" + sequence, "MALE", LocalDate.of(1999, 5, 20),
                avgPaceSecondsPerKm, new BigDecimal("70.5"), new BigDecimal("175.0")));
        return userId;
    }
}
