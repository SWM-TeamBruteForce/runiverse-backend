package com.runiverse.running_service.application.match.exception;

import com.runiverse.running_service.application.common.exception.BusinessException;
import com.runiverse.running_service.application.common.exception.RoomChatErrorCode;

// 이 방에 배정돼 있지만 러닝이 시작됐거나 방이 닫혔다(STARTED·FINISHED·CANCELLED).
// 배정 판정(404)보다 뒤에 본다 — 방 밖의 사람에게는 방 상태를 알려 주지 않는다
public class RoomChatClosedException extends BusinessException {

    public RoomChatClosedException() {
        super(RoomChatErrorCode.ROOM_CHAT_CLOSED);
    }
}
