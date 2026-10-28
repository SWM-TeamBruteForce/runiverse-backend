package com.runiverse.running_service.application.match.exception;

import com.runiverse.running_service.application.common.exception.BusinessException;
import com.runiverse.running_service.application.common.exception.InviteErrorCode;

// 친구가 러닝 중이다 — 시작된 방에 있거나 솔로 방을 열었다(솔로는 모집 단계가 없어 시작 전이어도 러닝으로 본다)
public class InviteeRunningException extends BusinessException {

    public InviteeRunningException() {
        super(InviteErrorCode.INVITEE_RUNNING);
    }
}
