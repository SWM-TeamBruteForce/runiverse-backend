package com.runiverse.running_service.application.match.exception;

import com.runiverse.running_service.application.common.exception.BusinessException;
import com.runiverse.running_service.application.common.exception.ResourceErrorCode;

// 채팅할 방이 없거나, 솔로 방이거나, 호출자가 이 방에 배정돼 있지 않다(나갔거나 처음부터 아님).
// 셋을 가르지 않는다 — 남의 방이 있는지 없는지를 알려 주지 않는다
public class RoomChatRoomNotFoundException extends BusinessException {

    public RoomChatRoomNotFoundException() {
        super(ResourceErrorCode.NOT_FOUND);
    }
}
