package com.runiverse.running_service.domain.notification.exception;

import com.runiverse.running_service.domain.common.exception.BusinessException;
import com.runiverse.running_service.domain.common.exception.UserNotificationErrorCode;

public class SelfNotificationException extends BusinessException {

    public SelfNotificationException() {
        super(UserNotificationErrorCode.SELF_NOTIFICATION);
    }
}
