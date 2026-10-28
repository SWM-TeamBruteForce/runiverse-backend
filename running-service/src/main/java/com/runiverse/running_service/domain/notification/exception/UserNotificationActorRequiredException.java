package com.runiverse.running_service.domain.notification.exception;

import com.runiverse.running_service.domain.common.exception.BusinessException;
import com.runiverse.running_service.domain.common.exception.UserNotificationErrorCode;

public class UserNotificationActorRequiredException extends BusinessException {

    public UserNotificationActorRequiredException() {
        super(UserNotificationErrorCode.USER_NOTIFICATION_ACTOR_REQUIRED);
    }
}
