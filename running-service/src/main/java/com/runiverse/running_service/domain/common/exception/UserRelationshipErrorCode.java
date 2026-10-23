package com.runiverse.running_service.domain.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum UserRelationshipErrorCode implements ErrorCode {
    INVALID_USER_RELATIONSHIP_ID("INVALID_USER_RELATIONSHIP_ID", "관계 ID가 올바르지 않습니다."),
    RELATIONSHIP_TYPE_REQUIRED("RELATIONSHIP_TYPE_REQUIRED", "관계 종류는 필수입니다."),
    SELF_RELATIONSHIP("SELF_RELATIONSHIP", "자기 자신과는 관계를 맺을 수 없습니다."),
    INVALID_RELATIONSHIP_TRANSITION("INVALID_RELATIONSHIP_TRANSITION", "허용되지 않는 관계 변경입니다.");
    private final String code;
    private final String message;
}
