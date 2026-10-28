package com.runiverse.running_service.application.match.exception;

import com.runiverse.running_service.application.common.exception.BusinessException;
import com.runiverse.running_service.application.common.exception.ResourceErrorCode;

// 초대 보내기에서 방이 없거나, 초대 방이 아니거나, 호출자가 이 방의 현재 참가자가 아니다.
// 셋을 가르지 않는다 — 남의 방이 있는지 없는지를 알려 주지 않는다
public class InviteRoomNotFoundException extends BusinessException {

    public InviteRoomNotFoundException() {
        super(ResourceErrorCode.NOT_FOUND);
    }
}
