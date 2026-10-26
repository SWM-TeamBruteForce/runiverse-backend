package com.runiverse.running_service.application.relationship.exception;

import com.runiverse.running_service.application.common.exception.BusinessException;
import com.runiverse.running_service.application.common.exception.RelationshipErrorCode;

// 사용자 검색 커서가 깨졌다
public class InvalidUserSearchCursorException extends BusinessException {

    public InvalidUserSearchCursorException() {
        super(RelationshipErrorCode.INVALID_USER_SEARCH_CURSOR);
    }
}
