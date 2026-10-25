package com.runiverse.running_service.presentation.relationship.response;

import com.runiverse.running_service.application.relationship.query.recommendation.GetFriendRecommendationsResult;

import java.util.List;
import java.util.UUID;

public record FriendRecommendationsResponse(List<FriendRecommendationResponse> recommendations) {

    public static FriendRecommendationsResponse from(GetFriendRecommendationsResult result) {
        return new FriendRecommendationsResponse(result.recommendations().stream()
                .map(FriendRecommendationResponse::from)
                .toList());
    }

    // 결과는 모두 친구 요청을 보낼 수 있는 사람이라 relationshipType을 싣지 않는다
    public record FriendRecommendationResponse(
            UUID userId,
            String nickname,
            String profileImageUrl,     // 사진이 없으면 null
            String reason               // RAN_TOGETHER | SIMILAR_PACE
    ) {

        static FriendRecommendationResponse from(GetFriendRecommendationsResult.Recommendation recommendation) {
            return new FriendRecommendationResponse(
                    recommendation.userId(),
                    recommendation.nickname(),
                    recommendation.profileImageUrl(),
                    recommendation.reason().name());
        }
    }
}
