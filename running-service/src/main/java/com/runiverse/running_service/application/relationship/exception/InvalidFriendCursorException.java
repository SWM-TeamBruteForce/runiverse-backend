package com.runiverse.running_service.application.relationship.exception;

import com.runiverse.running_service.application.common.exception.BusinessException;
import com.runiverse.running_service.application.common.exception.RelationshipErrorCode;

// 커서가 깨졌거나 친구 목록에 없는 묶음을 가리킨다
public class InvalidFriendCursorException extends BusinessException {

    public InvalidFriendCursorException() {
        super(RelationshipErrorCode.INVALID_FRIEND_CURSOR);
    }
}
