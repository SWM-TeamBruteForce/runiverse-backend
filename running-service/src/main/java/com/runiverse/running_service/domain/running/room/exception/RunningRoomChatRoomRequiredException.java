package com.runiverse.running_service.domain.running.room.exception;

import com.runiverse.running_service.domain.common.exception.BusinessException;
import com.runiverse.running_service.domain.common.exception.RunningRoomChatErrorCode;

public class RunningRoomChatRoomRequiredException extends BusinessException {

    public RunningRoomChatRoomRequiredException() {
        super(RunningRoomChatErrorCode.RUNNING_ROOM_CHAT_ROOM_REQUIRED);
    }
}
