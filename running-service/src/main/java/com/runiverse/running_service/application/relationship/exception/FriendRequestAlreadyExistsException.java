package com.runiverse.running_service.application.relationship.exception;

import com.runiverse.running_service.application.common.exception.BusinessException;
import com.runiverse.running_service.application.common.exception.RelationshipErrorCode;

public class FriendRequestAlreadyExistsException extends BusinessException {

    public FriendRequestAlreadyExistsException() {
        super(RelationshipErrorCode.FRIEND_REQUEST_ALREADY_EXISTS);
    }
}
