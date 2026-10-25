package com.runiverse.running_service.application.relationship.exception;

import com.runiverse.running_service.application.common.exception.BusinessException;
import com.runiverse.running_service.application.common.exception.ResourceErrorCode;

// 목록 주인이 없다 — 탈퇴한 사용자도 없는 사용자와 똑같이 다룬다
public class FriendListOwnerNotFoundException extends BusinessException {

    public FriendListOwnerNotFoundException() {
        super(ResourceErrorCode.NOT_FOUND);
    }
}
