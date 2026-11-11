package com.runiverse.running_service.domain.running.room.exception;

import com.runiverse.running_service.domain.common.exception.BusinessException;
import com.runiverse.running_service.domain.common.exception.RunningRoomChatErrorCode;

public class RunningRoomChatSenderRequiredException extends BusinessException {

    public RunningRoomChatSenderRequiredException() {
        super(RunningRoomChatErrorCode.RUNNING_ROOM_CHAT_SENDER_REQUIRED);
    }
}
