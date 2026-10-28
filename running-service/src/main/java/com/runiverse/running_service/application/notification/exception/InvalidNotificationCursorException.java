package com.runiverse.running_service.application.notification.exception;

import com.runiverse.running_service.application.common.exception.BusinessException;
import com.runiverse.running_service.application.common.exception.NotificationErrorCode;

// 알림 목록 커서가 깨졌다 — 서버가 준 값을 그대로 돌려보내지 않았다
public class InvalidNotificationCursorException extends BusinessException {

    public InvalidNotificationCursorException() {
        super(NotificationErrorCode.INVALID_NOTIFICATION_CURSOR);
    }
}
