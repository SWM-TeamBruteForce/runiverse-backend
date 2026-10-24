package com.runiverse.running_service.application.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum RelationshipErrorCode implements ErrorCode {
    // 토큰 주체와 경로가 같다 — 값만으로 알 수 있지만 경로 하나로는 판정할 수 없어 유스케이스가 본다
    CANNOT_FRIEND_SELF("CANNOT_FRIEND_SELF", "자기 자신에게는 친구 요청을 보낼 수 없습니다."),
    // 내가 이미 요청했거나 이미 친구다 — 상대가 나에게 요청해 둔 경우는 여기가 아니라 수락으로 처리한다
    FRIEND_REQUEST_ALREADY_EXISTS("FRIEND_REQUEST_ALREADY_EXISTS", "이미 요청했거나 이미 친구입니다.");
    private final String code;
    private final String message;
}
