package com.runiverse.running_service.domain.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum RunningComboErrorCode implements ErrorCode {
    INVALID_COMBO_SEGMENT_NUMBER("INVALID_COMBO_SEGMENT_NUMBER", "콤보 구간 번호는 1 이상이어야 합니다."),
    INVALID_COMBO_COUNT("INVALID_COMBO_COUNT", "콤보 유지 횟수는 1 이상이어야 합니다."),
    COMBO_USERS_NOT_ORDERED("COMBO_USERS_NOT_ORDERED", "콤보 참가자 쌍은 서로 다른 두 사람을 정렬해 담아야 합니다."),
    COMBO_ROUTE_REQUIRED("COMBO_ROUTE_REQUIRED", "콤보 달성 위치는 최소 하나 필요합니다."),
    COMBO_ROUTE_SIZE_MISMATCH("COMBO_ROUTE_SIZE_MISMATCH", "콤보 달성 위치 수가 유지 횟수와 다릅니다.");
    private final String code;
    private final String message;
}
