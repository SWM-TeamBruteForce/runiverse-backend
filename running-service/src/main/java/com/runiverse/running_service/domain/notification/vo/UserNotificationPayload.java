package com.runiverse.running_service.domain.notification.vo;

import com.runiverse.running_service.domain.common.vo.UserId;

// 알림 종류마다 따로 저장하는 값 — 구현체 하나가 종류 하나다.
// 종류를 구현체가 정하므로 type과 payload가 어긋난 알림은 만들 수 없다
public sealed interface UserNotificationPayload
        permits FriendRequestReceivedPayload, FriendRequestAcceptedPayload, RunningInvitationReceivedPayload {

    UserNotificationType type();

    // 알림을 일으킨 사람 — 종류마다 이름은 달라도(요청한 사람·수락한 사람·초대한 사람) 모두 한 명씩 있다
    UserId actorId();
}
