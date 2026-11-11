package com.runiverse.running_service.application.match.exception;

import com.runiverse.running_service.application.common.exception.BusinessException;
import com.runiverse.running_service.application.common.exception.ResourceErrorCode;

// 고치거나 지울 메시지가 이 방에 없거나, 이미 지웠거나, 내 메시지가 아니다.
// 셋을 가르지 않는다 — 남의 메시지가 있는지 없는지를 알려 주지 않는다.
// 도메인도 같은 조건을 막지만 도메인 예외는 500으로 나간다 — 404로 보이려면 여기서 먼저 거른다
public class RoomChatNotFoundException extends BusinessException {

    public RoomChatNotFoundException() {
        super(ResourceErrorCode.NOT_FOUND);
    }
}
