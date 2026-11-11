package com.runiverse.running_service.domain.running.room.exception;

import com.runiverse.running_service.domain.common.exception.BusinessException;
import com.runiverse.running_service.domain.common.exception.RunningRoomChatErrorCode;

public class NotRunningRoomChatSenderException extends BusinessException {

    public NotRunningRoomChatSenderException() {
        super(RunningRoomChatErrorCode.NOT_RUNNING_ROOM_CHAT_SENDER);
    }
}
