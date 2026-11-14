package com.runiverse.running_service.domain.running.room.exception;

import com.runiverse.running_service.domain.common.exception.BusinessException;
import com.runiverse.running_service.domain.common.exception.RunningRoomChatErrorCode;

public class RunningRoomChatChangedAtRequiredException extends BusinessException {

    public RunningRoomChatChangedAtRequiredException() {
        super(RunningRoomChatErrorCode.RUNNING_ROOM_CHAT_CHANGED_AT_REQUIRED);
    }
}
