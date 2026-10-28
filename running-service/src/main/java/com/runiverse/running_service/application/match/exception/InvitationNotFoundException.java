package com.runiverse.running_service.application.match.exception;

import com.runiverse.running_service.application.common.exception.BusinessException;
import com.runiverse.running_service.application.common.exception.ResourceErrorCode;

// 수락·거절에서 방이 없거나 이 방에서 나에게 온 초대가 없다. 둘을 가르지 않는다
public class InvitationNotFoundException extends BusinessException {

    public InvitationNotFoundException() {
        super(ResourceErrorCode.NOT_FOUND);
    }
}
