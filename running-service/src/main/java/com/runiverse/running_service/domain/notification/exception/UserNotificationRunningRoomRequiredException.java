package com.runiverse.running_service.domain.notification.exception;

import com.runiverse.running_service.domain.common.exception.BusinessException;
import com.runiverse.running_service.domain.common.exception.UserNotificationErrorCode;

public class UserNotificationRunningRoomRequiredException extends BusinessException {

    public UserNotificationRunningRoomRequiredException() {
        super(UserNotificationErrorCode.USER_NOTIFICATION_RUNNING_ROOM_REQUIRED);
    }
}
