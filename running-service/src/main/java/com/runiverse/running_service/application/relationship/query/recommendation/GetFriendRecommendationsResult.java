package com.runiverse.running_service.application.relationship.query.recommendation;

import java.util.List;
import java.util.UUID;

public record GetFriendRecommendationsResult(List<Recommendation> recommendations) {

    public record Recommendation(
            UUID userId,
            String nickname,
            String profileImageUrl,         // 사진이 없으면 null
            RecommendationReason reason
    ) {

    }
}
