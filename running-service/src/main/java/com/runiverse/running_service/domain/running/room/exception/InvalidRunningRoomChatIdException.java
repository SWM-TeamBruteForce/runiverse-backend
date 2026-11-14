package com.runiverse.running_service.domain.running.room.exception;

import com.runiverse.running_service.domain.common.exception.BusinessException;
import com.runiverse.running_service.domain.common.exception.RunningRoomChatErrorCode;

public class InvalidRunningRoomChatIdException extends BusinessException {

    public InvalidRunningRoomChatIdException() {
        super(RunningRoomChatErrorCode.INVALID_RUNNING_ROOM_CHAT_ID);
    }
}
