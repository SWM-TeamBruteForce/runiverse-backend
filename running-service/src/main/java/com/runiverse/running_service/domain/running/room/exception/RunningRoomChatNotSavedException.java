package com.runiverse.running_service.domain.running.room.exception;

import com.runiverse.running_service.domain.common.exception.BusinessException;
import com.runiverse.running_service.domain.common.exception.RunningRoomChatErrorCode;

public class RunningRoomChatNotSavedException extends BusinessException {

    public RunningRoomChatNotSavedException() {
        super(RunningRoomChatErrorCode.RUNNING_ROOM_CHAT_NOT_SAVED);
    }
}
