package com.runiverse.running_service.application.match.exception;

import com.runiverse.running_service.application.common.exception.BusinessException;
import com.runiverse.running_service.application.common.exception.InviteErrorCode;

// 자리가 다 찼다 — 초대는 PENDING으로 남아 누가 나가면 다시 수락할 수 있다
public class RunningRoomFullException extends BusinessException {

    public RunningRoomFullException() {
        super(InviteErrorCode.RUNNING_ROOM_FULL);
    }
}
