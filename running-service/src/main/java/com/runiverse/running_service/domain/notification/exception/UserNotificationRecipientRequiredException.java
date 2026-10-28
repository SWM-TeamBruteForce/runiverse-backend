package com.runiverse.running_service.domain.notification.exception;

import com.runiverse.running_service.domain.common.exception.BusinessException;
import com.runiverse.running_service.domain.common.exception.UserNotificationErrorCode;

public class UserNotificationRecipientRequiredException extends BusinessException {

    public UserNotificationRecipientRequiredException() {
        super(UserNotificationErrorCode.USER_NOTIFICATION_RECIPIENT_REQUIRED);
    }
}
