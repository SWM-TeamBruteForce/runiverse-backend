package com.runiverse.running_service.application.match.exception;

import com.runiverse.running_service.application.common.exception.BusinessException;
import com.runiverse.running_service.application.common.exception.InviteErrorCode;

// 친구가 다른 매칭 방·초대 방에서 대기 중이다(모집 중·확정 후 시작 전) — 활성 신청은 하나라 초대를 받아도 수락할 수 없다
public class InviteeInMatchingException extends BusinessException {

    public InviteeInMatchingException() {
        super(InviteErrorCode.INVITEE_IN_MATCHING);
    }
}
