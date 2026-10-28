package com.runiverse.running_service.application.match.exception;

import com.runiverse.running_service.application.common.exception.BusinessException;
import com.runiverse.running_service.application.common.exception.InviteErrorCode;

// 모집 중이 아니거나 모집 마감(start_at - 초대 마감 오프셋)이 지났다 — 초대·수락이 함께 던진다
public class RunningInvitationClosedException extends BusinessException {

    public RunningInvitationClosedException() {
        super(InviteErrorCode.RUNNING_INVITATION_CLOSED);
    }
}
