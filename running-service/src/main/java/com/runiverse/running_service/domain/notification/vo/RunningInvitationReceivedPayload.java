package com.runiverse.running_service.domain.notification.vo;

import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.notification.exception.UserNotificationActorRequiredException;
import com.runiverse.running_service.domain.notification.exception.UserNotificationRunningRoomRequiredException;
import com.runiverse.running_service.domain.running.room.vo.RunningRoomId;

// 누군가 나를 초대 방에 초대했다 — 저장 키는 request_user_id·running_room_id.
// 방과 받는 사람으로 그 초대(running_room_invitations)를 찾아 지금 상태를 붙인다
public record RunningInvitationReceivedPayload(UserId requestUserId, RunningRoomId runningRoomId)
        implements UserNotificationPayload {

    public RunningInvitationReceivedPayload {
        if (requestUserId == null) {
            throw new UserNotificationActorRequiredException();
        }
        if (runningRoomId == null) {
            throw new UserNotificationRunningRoomRequiredException();
        }
    }

    @Override
    public UserNotificationType type() {
        return UserNotificationType.RUNNING_INVITATION_RECEIVED;
    }

    @Override
    public UserId actorId() {
        return requestUserId;
    }
}
