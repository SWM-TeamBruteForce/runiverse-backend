package com.runiverse.running_service.domain.relationship.exception;

import com.runiverse.running_service.domain.common.exception.BusinessException;
import com.runiverse.running_service.domain.common.exception.UserRelationshipErrorCode;

public class InvalidUserRelationshipIdException extends BusinessException {

    public InvalidUserRelationshipIdException() {
        super(UserRelationshipErrorCode.INVALID_USER_RELATIONSHIP_ID);
    }
}
