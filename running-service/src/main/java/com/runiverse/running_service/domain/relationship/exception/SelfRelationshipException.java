package com.runiverse.running_service.domain.relationship.exception;

import com.runiverse.running_service.domain.common.exception.BusinessException;
import com.runiverse.running_service.domain.common.exception.UserRelationshipErrorCode;

public class SelfRelationshipException extends BusinessException {

    public SelfRelationshipException() {
        super(UserRelationshipErrorCode.SELF_RELATIONSHIP);
    }
}
