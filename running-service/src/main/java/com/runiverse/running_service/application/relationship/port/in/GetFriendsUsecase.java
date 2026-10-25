package com.runiverse.running_service.application.relationship.port.in;

import com.runiverse.running_service.application.relationship.query.friends.GetFriendsQuery;
import com.runiverse.running_service.application.relationship.query.friends.GetFriendsResult;

public interface GetFriendsUsecase {

    GetFriendsResult handle(GetFriendsQuery query);
}
