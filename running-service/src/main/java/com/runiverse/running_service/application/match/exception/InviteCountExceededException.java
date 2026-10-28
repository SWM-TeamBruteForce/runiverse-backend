package com.runiverse.running_service.application.match.exception;

import com.runiverse.running_service.application.common.exception.BusinessException;
import com.runiverse.running_service.application.common.exception.InviteErrorCode;

// 호출자가 이 친구에게 보낸 횟수가 상한(invite.max-invite-count)에 닿았다 — 거절·나감 뒤에도 이어서 센다
public class InviteCountExceededException extends BusinessException {

    public InviteCountExceededException() {
        super(InviteErrorCode.INVITE_COUNT_EXCEEDED);
    }
}
