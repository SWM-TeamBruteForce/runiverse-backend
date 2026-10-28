package com.runiverse.running_service.domain.notification.vo;

import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.notification.exception.UserNotificationActorRequiredException;

// 누군가 나에게 친구 요청을 보냈다 — 저장 키는 request_user_id
public record FriendRequestedPayload(UserId requestUserId) implements UserNotificationPayload {

    public FriendRequestedPayload {
        if (requestUserId == null) {
            throw new UserNotificationActorRequiredException();
        }
    }

    @Override
    public UserNotificationType type() {
        return UserNotificationType.FRIEND_REQUESTED;
    }

    @Override
    public UserId actorId() {
        return requestUserId;
    }
}
