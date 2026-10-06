package com.runiverse.running_service.application.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

// 리소스 종류를 가리지 않는다 — 러닝방·기록·피드·댓글도 같은 코드를 쓴다
@Getter
@RequiredArgsConstructor
public enum ResourceErrorCode implements ErrorCode {
    NOT_FOUND("NOT_FOUND", "요청한 리소스를 찾을 수 없습니다.");
    private final String code;
    private final String message;
}
