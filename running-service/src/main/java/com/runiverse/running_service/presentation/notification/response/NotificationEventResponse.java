package com.runiverse.running_service.presentation.notification.response;

import com.runiverse.running_service.application.notification.query.list.GetUserNotificationsResult;

import java.time.LocalDateTime;

// 앱 스트림 알림 이벤트의 data — 목록(2-4) 항목에서 type만 뺀 모양이다.
// 종류는 SSE event 이름(FRIEND_REQUESTED 등)이 말한다(매칭 스트림과 같다). 사람·초대는 목록과 같은 변환을 쓴다
public record NotificationEventResponse(
        Long notificationId,
        boolean isRead,
        LocalDateTime createdAt,
        UserNotificationsResponse.ActorResponse actor,
        UserNotificationsResponse.InvitationResponse invitation   // RUNNING_INVITED만 있고 그 밖에는 null
) {

    public static NotificationEventResponse from(GetUserNotificationsResult.Item item) {
        return new NotificationEventResponse(
                item.notificationId(),
                item.read(),
                item.createdAt(),
                UserNotificationsResponse.ActorResponse.from(item.actor()),
                item.invitation() == null ? null : UserNotificationsResponse.InvitationResponse.from(item.invitation()));
    }
}
