package com.runiverse.running_service.domain.notification.vo;

import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.notification.exception.UserNotificationActorRequiredException;

// 내가 보낸 친구 요청을 상대가 수락했다 — 저장 키는 accept_user_id
public record FriendAcceptedPayload(UserId acceptUserId) implements UserNotificationPayload {

    public FriendAcceptedPayload {
        if (acceptUserId == null) {
            throw new UserNotificationActorRequiredException();
        }
    }

    @Override
    public UserNotificationType type() {
        return UserNotificationType.FRIEND_ACCEPTED;
    }

    @Override
    public UserId actorId() {
        return acceptUserId;
    }
}
