package com.runiverse.running_service.application.match.exception;

import com.runiverse.running_service.application.common.exception.BusinessException;
import com.runiverse.running_service.application.common.exception.InviteErrorCode;

// 문구는 InviteStartPolicy가 운영값으로 만든다 — 설정이 바뀌면 안내 문구도 함께 바뀌어야 한다
public class InvalidInviteOptionException extends BusinessException {

    public InvalidInviteOptionException(String message) {
        super(InviteErrorCode.INVALID_INVITE_OPTION, message);
    }
}
