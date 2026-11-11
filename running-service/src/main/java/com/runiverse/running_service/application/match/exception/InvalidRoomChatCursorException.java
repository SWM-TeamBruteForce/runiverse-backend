package com.runiverse.running_service.application.match.exception;

import com.runiverse.running_service.application.common.exception.BusinessException;
import com.runiverse.running_service.application.common.exception.RoomChatErrorCode;

// 채팅 목록 커서가 깨졌다 — 서버가 준 값을 그대로 돌려보내지 않았다
public class InvalidRoomChatCursorException extends BusinessException {

    public InvalidRoomChatCursorException() {
        super(RoomChatErrorCode.INVALID_ROOM_CHAT_CURSOR);
    }
}
