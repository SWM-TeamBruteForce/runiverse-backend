package com.runiverse.running_service.application.relationship.port.in;

import com.runiverse.running_service.application.relationship.query.recommendation.GetFriendRecommendationsQuery;
import com.runiverse.running_service.application.relationship.query.recommendation.GetFriendRecommendationsResult;

public interface GetFriendRecommendationsUsecase {

    GetFriendRecommendationsResult handle(GetFriendRecommendationsQuery query);
}
