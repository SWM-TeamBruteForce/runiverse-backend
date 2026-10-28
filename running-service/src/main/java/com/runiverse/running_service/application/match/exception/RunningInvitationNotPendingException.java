package com.runiverse.running_service.application.match.exception;

import com.runiverse.running_service.application.common.exception.BusinessException;
import com.runiverse.running_service.application.common.exception.InviteErrorCode;

// 이미 수락·거절했거나 나갔거나 만료됐다(수락은 만료를 마감으로 먼저 거른다) — 거절·나감은 다시 초대받으면 대기로 돌아온다
public class RunningInvitationNotPendingException extends BusinessException {

    public RunningInvitationNotPendingException() {
        super(InviteErrorCode.RUNNING_INVITATION_NOT_PENDING);
    }
}
