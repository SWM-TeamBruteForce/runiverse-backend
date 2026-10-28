package com.runiverse.running_service.application.match.exception;

import com.runiverse.running_service.application.common.exception.BusinessException;
import com.runiverse.running_service.application.common.exception.InviteErrorCode;

// 친구가 아니면 보내지 않는다 — 친구 목록에서 고른 뒤 관계가 바뀌었을 수 있다
public class NotFriendException extends BusinessException {

    public NotFriendException() {
        super(InviteErrorCode.NOT_FRIEND);
    }
}
