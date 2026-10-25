package com.runiverse.running_service.unit_test.relationship.application;

import com.github.f4b6a3.uuid.UuidCreator;
import com.runiverse.running_service.application.common.port.out.LoadUserAvgPacePort;
import com.runiverse.running_service.application.relationship.port.out.LoadFriendRecommendationsPort;
import com.runiverse.running_service.application.relationship.port.out.RecommendationCandidate;
import com.runiverse.running_service.application.relationship.query.recommendation.FriendRecommendationProperties;
import com.runiverse.running_service.application.relationship.query.recommendation.GetFriendRecommendationsHandler;
import com.runiverse.running_service.application.relationship.query.recommendation.GetFriendRecommendationsQuery;
import com.runiverse.running_service.application.relationship.query.recommendation.GetFriendRecommendationsResult;
import com.runiverse.running_service.application.relationship.query.recommendation.RecommendationReason;
import com.runiverse.running_service.application.user.port.out.GenerateViewUrlPort;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.running.metric.vo.Pace;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("추천 친구 조회 단위 테스트")
public class GetFriendRecommendationsHandlerTest {

    private static final int MAX_COUNT = 5;
    private static final int TOLERANCE = 20;
    private static final int MY_PACE = 330;

    @Mock
    private LoadFriendRecommendationsPort loadFriendRecommendationsPort;

    @Mock
    private LoadUserAvgPacePort loadUserAvgPacePort;

    @Mock
    private GenerateViewUrlPort generateViewUrlPort;

    private GetFriendRecommendationsHandler handler;

    private UUID userId;

    @BeforeEach
    void setUp() {
        handler = new GetFriendRecommendationsHandler(loadFriendRecommendationsPort, loadUserAvgPacePort,
                generateViewUrlPort, new FriendRecommendationProperties(MAX_COUNT, TOLERANCE));
        userId = UuidCreator.getTimeOrderedEpoch();
    }

    @Test
    @DisplayName("함께 뛴 사람을 먼저 채우고 남은 자리만큼 페이스가 비슷한 사람을 이어 붙인다")
    void ranTogetherFirstThenSimilarPace() {
        // given
        RecommendationCandidate runner = candidate(null);
        RecommendationCandidate similar = candidate(null);
        given(loadFriendRecommendationsPort.loadRanTogether(new UserId(userId), MAX_COUNT)).willReturn(List.of(runner));
        given(loadUserAvgPacePort.loadAvgPace(new UserId(userId))).willReturn(Optional.of(new Pace(MY_PACE)));
        // 앞 갈래에서 뽑은 사람을 빼고 남은 4자리만 읽는다
        given(loadFriendRecommendationsPort.loadSimilarPace(
                new UserId(userId), MY_PACE, TOLERANCE, List.of(runner.userId()), MAX_COUNT - 1))
                .willReturn(List.of(similar));

        // when
        GetFriendRecommendationsResult result = handler.handle(new GetFriendRecommendationsQuery(userId));

        // then
        assertThat(result.recommendations()).extracting(GetFriendRecommendationsResult.Recommendation::userId)
                .containsExactly(runner.userId(), similar.userId());
        assertThat(result.recommendations()).extracting(GetFriendRecommendationsResult.Recommendation::reason)
                .containsExactly(RecommendationReason.RAN_TOGETHER, RecommendationReason.SIMILAR_PACE);
    }

    @Test
    @DisplayName("함께 뛴 사람이 없으면 페이스가 비슷한 사람으로 전부 채우고 뺄 사람은 없다")
    void noRanTogetherFillsAllWithSimilarPace() {
        // given -> 매칭 러닝을 한 적이 없거나 마지막 매칭 상대가 모두 이미 친구인 경우다
        RecommendationCandidate similar = candidate(null);
        given(loadFriendRecommendationsPort.loadRanTogether(new UserId(userId), MAX_COUNT)).willReturn(List.of());
        given(loadUserAvgPacePort.loadAvgPace(new UserId(userId))).willReturn(Optional.of(new Pace(MY_PACE)));
        given(loadFriendRecommendationsPort.loadSimilarPace(
                new UserId(userId), MY_PACE, TOLERANCE, List.of(), MAX_COUNT))
                .willReturn(List.of(similar));

        // when
        GetFriendRecommendationsResult result = handler.handle(new GetFriendRecommendationsQuery(userId));

        // then
        assertThat(result.recommendations()).extracting(GetFriendRecommendationsResult.Recommendation::reason)
                .containsExactly(RecommendationReason.SIMILAR_PACE);
    }

    @Test
    @DisplayName("함께 뛴 사람으로 총 인원이 차면 페이스 갈래는 읽지 않는다")
    void fullRanTogetherSkipsSimilarPace() {
        // given
        List<RecommendationCandidate> runners = IntStream.range(0, MAX_COUNT).mapToObj(i -> candidate(null)).toList();
        given(loadFriendRecommendationsPort.loadRanTogether(new UserId(userId), MAX_COUNT)).willReturn(runners);
        given(loadUserAvgPacePort.loadAvgPace(new UserId(userId))).willReturn(Optional.of(new Pace(MY_PACE)));

        // when
        GetFriendRecommendationsResult result = handler.handle(new GetFriendRecommendationsQuery(userId));

        // then
        assertThat(result.recommendations()).hasSize(MAX_COUNT)
                .allSatisfy(recommendation ->
                        assertThat(recommendation.reason()).isEqualTo(RecommendationReason.RAN_TOGETHER));
        verify(loadFriendRecommendationsPort, never()).loadSimilarPace(any(), anyInt(), anyInt(), any(), anyInt());
    }

    @Test
    @DisplayName("온보딩 전이라 기준 페이스가 없으면 페이스 갈래는 비고 함께 뛴 사람만 나온다")
    void noPaceSkipsSimilarPace() {
        // given
        RecommendationCandidate runner = candidate(null);
        given(loadFriendRecommendationsPort.loadRanTogether(new UserId(userId), MAX_COUNT)).willReturn(List.of(runner));
        given(loadUserAvgPacePort.loadAvgPace(new UserId(userId))).willReturn(Optional.empty());

        // when
        GetFriendRecommendationsResult result = handler.handle(new GetFriendRecommendationsQuery(userId));

        // then
        assertThat(result.recommendations()).extracting(GetFriendRecommendationsResult.Recommendation::userId)
                .containsExactly(runner.userId());
        verify(loadFriendRecommendationsPort, never()).loadSimilarPace(any(), anyInt(), anyInt(), any(), anyInt());
    }

    @Test
    @DisplayName("후보가 하나도 없으면 빈 목록이다")
    void noCandidatesIsEmpty() {
        // given
        given(loadFriendRecommendationsPort.loadRanTogether(new UserId(userId), MAX_COUNT)).willReturn(List.of());
        given(loadUserAvgPacePort.loadAvgPace(new UserId(userId))).willReturn(Optional.of(new Pace(MY_PACE)));
        given(loadFriendRecommendationsPort.loadSimilarPace(
                new UserId(userId), MY_PACE, TOLERANCE, List.of(), MAX_COUNT)).willReturn(List.of());

        // when & then
        assertThat(handler.handle(new GetFriendRecommendationsQuery(userId)).recommendations()).isEmpty();
    }

    @Test
    @DisplayName("사진이 있으면 조회 URL을 만들고 없으면 null이다")
    void profileImageUrlOnlyWhenKeyExists() {
        // given
        RecommendationCandidate withImage = candidate("profile/a.png");
        RecommendationCandidate withoutImage = candidate(null);
        given(loadFriendRecommendationsPort.loadRanTogether(new UserId(userId), MAX_COUNT))
                .willReturn(List.of(withImage, withoutImage));
        given(loadUserAvgPacePort.loadAvgPace(new UserId(userId))).willReturn(Optional.empty());
        given(generateViewUrlPort.generate("profile/a.png")).willReturn("https://cdn/profile/a.png");

        // when
        GetFriendRecommendationsResult result = handler.handle(new GetFriendRecommendationsQuery(userId));

        // then
        assertThat(result.recommendations()).extracting(GetFriendRecommendationsResult.Recommendation::profileImageUrl)
                .containsExactly("https://cdn/profile/a.png", null);
    }

    private static RecommendationCandidate candidate(String profileImageKey) {
        return new RecommendationCandidate(UuidCreator.getTimeOrderedEpoch(), "닉네임", profileImageKey);
    }
}
