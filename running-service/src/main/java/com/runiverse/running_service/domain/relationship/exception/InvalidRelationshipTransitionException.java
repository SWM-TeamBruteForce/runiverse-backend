package com.runiverse.running_service.domain.relationship.exception;

import com.runiverse.running_service.domain.common.exception.BusinessException;
import com.runiverse.running_service.domain.common.exception.UserRelationshipErrorCode;

public class InvalidRelationshipTransitionException extends BusinessException {

    public InvalidRelationshipTransitionException() {
        super(UserRelationshipErrorCode.INVALID_RELATIONSHIP_TRANSITION);
    }
}
