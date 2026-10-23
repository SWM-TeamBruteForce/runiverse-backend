package com.runiverse.running_service.domain.relationship.exception;

import com.runiverse.running_service.domain.common.exception.BusinessException;
import com.runiverse.running_service.domain.common.exception.UserRelationshipErrorCode;

public class RelationshipTypeRequiredException extends BusinessException {

    public RelationshipTypeRequiredException() {
        super(UserRelationshipErrorCode.RELATIONSHIP_TYPE_REQUIRED);
    }
}
