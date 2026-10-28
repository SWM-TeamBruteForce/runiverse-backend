package com.runiverse.running_service.domain.notification.exception;

import com.runiverse.running_service.domain.common.exception.BusinessException;
import com.runiverse.running_service.domain.common.exception.UserNotificationErrorCode;

public class UserNotificationPayloadRequiredException extends BusinessException {

    public UserNotificationPayloadRequiredException() {
        super(UserNotificationErrorCode.USER_NOTIFICATION_PAYLOAD_REQUIRED);
    }
}
