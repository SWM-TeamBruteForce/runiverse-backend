package com.runiverse.running_service.application.relationship.exception;

import com.runiverse.running_service.application.common.exception.BusinessException;
import com.runiverse.running_service.application.common.exception.RelationshipErrorCode;

public class CannotFriendSelfException extends BusinessException {

    public CannotFriendSelfException() {
        super(RelationshipErrorCode.CANNOT_FRIEND_SELF);
    }
}
