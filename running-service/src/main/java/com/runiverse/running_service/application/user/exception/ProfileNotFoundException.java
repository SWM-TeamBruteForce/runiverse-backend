package com.runiverse.running_service.application.user.exception;

import com.runiverse.running_service.application.common.exception.BusinessException;
import com.runiverse.running_service.application.common.exception.ResourceErrorCode;

// 탈퇴한 사용자도 없는 사용자와 똑같이 다룬다 — 탈퇴 여부를 노출하지 않는다
public class ProfileNotFoundException extends BusinessException {

    public ProfileNotFoundException() {
        super(ResourceErrorCode.NOT_FOUND);
    }
}
