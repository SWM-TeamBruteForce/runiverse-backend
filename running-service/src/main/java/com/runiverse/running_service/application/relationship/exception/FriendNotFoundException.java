package com.runiverse.running_service.application.relationship.exception;

import com.runiverse.running_service.application.common.exception.BusinessException;
import com.runiverse.running_service.application.common.exception.ResourceErrorCode;

// 삭제할 친구 관계가 없다 — 요청 중인 관계와 상대가 탈퇴한 경우도 같다
public class FriendNotFoundException extends BusinessException {

    public FriendNotFoundException() {
        super(ResourceErrorCode.NOT_FOUND);
    }
}
