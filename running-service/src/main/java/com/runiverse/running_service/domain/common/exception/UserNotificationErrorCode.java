package com.runiverse.running_service.domain.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum UserNotificationErrorCode implements ErrorCode {
    USER_NOTIFICATION_RECIPIENT_REQUIRED("USER_NOTIFICATION_RECIPIENT_REQUIRED", "알림을 받는 사람은 필수입니다."),
    USER_NOTIFICATION_PAYLOAD_REQUIRED("USER_NOTIFICATION_PAYLOAD_REQUIRED", "알림 내용은 필수입니다."),
    USER_NOTIFICATION_ACTOR_REQUIRED("USER_NOTIFICATION_ACTOR_REQUIRED", "알림을 일으킨 사람은 필수입니다."),
    USER_NOTIFICATION_RUNNING_ROOM_REQUIRED("USER_NOTIFICATION_RUNNING_ROOM_REQUIRED", "초대 알림에는 방이 필요합니다."),
    SELF_NOTIFICATION("SELF_NOTIFICATION", "자기 자신에게 알림을 보낼 수 없습니다.");
    private final String code;
    private final String message;
}
