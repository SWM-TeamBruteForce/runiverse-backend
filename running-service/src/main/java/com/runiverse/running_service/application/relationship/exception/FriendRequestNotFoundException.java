package com.runiverse.running_service.application.relationship.exception;

import com.runiverse.running_service.application.common.exception.BusinessException;
import com.runiverse.running_service.application.common.exception.ResourceErrorCode;

// 수락·취소·거절할 요청이 없다 — 취소·거절은 친구 관계도 없을 때다. 내가 보낸 요청을 내가 수락하려는 경우와 상대가 탈퇴한 경우도 같다
public class FriendRequestNotFoundException extends BusinessException {

    public FriendRequestNotFoundException() {
        super(ResourceErrorCode.NOT_FOUND);
    }
}
