package com.runiverse.running_service.domain.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum RunningRecordErrorCode implements ErrorCode {
    INVALID_SPLIT_ID("INVALID_SPLIT_ID", "구간 ID가 올바르지 않습니다."),
    INVALID_RUNNING_RECORD_ID("INVALID_RUNNING_RECORD_ID", "러닝 기록 ID가 올바르지 않습니다."),
    INVALID_SPLIT_NUMBER("INVALID_SPLIT_NUMBER", "구간 번호는 1 이상이어야 합니다."),
    ROUTE_POLYLINE_REQUIRED("ROUTE_POLYLINE_REQUIRED", "경로 데이터는 필수입니다."),
    GPS_TRACK_KEY_REQUIRED("GPS_TRACK_KEY_REQUIRED", "GPS 트랙 키는 필수입니다."),
    GPS_TRACK_KEY_TOO_LONG("GPS_TRACK_KEY_TOO_LONG", "GPS 트랙 키는 255자를 초과할 수 없습니다."),
    INVALID_ROUTE_RANGE("INVALID_ROUTE_RANGE", "구간 경로 범위가 올바르지 않습니다."),
    // split 관련 에러 코드
    SPLITS_REQUIRED("SPLITS_REQUIRED", "러닝 기록에는 구간이 최소 하나 필요합니다."),
    SPLIT_NUMBER_NOT_SEQUENTIAL("SPLIT_NUMBER_NOT_SEQUENTIAL", "구간 번호는 1부터 빠짐없이 이어져야 합니다."),
    SPLIT_PERIOD_OUT_OF_RECORD("SPLIT_PERIOD_OUT_OF_RECORD", "구간 시각이 러닝 기록 시각을 벗어났습니다."),
    SPLIT_ROUTE_NOT_STARTING_AT_ORIGIN("SPLIT_ROUTE_NOT_STARTING_AT_ORIGIN", "첫 구간의 경로는 0번 좌표에서 시작해야 합니다."),
    SPLIT_ROUTE_NOT_CONNECTED("SPLIT_ROUTE_NOT_CONNECTED", "구간 경로가 이어지지 않습니다."),
    SPLIT_PERIOD_NOT_SEQUENTIAL("SPLIT_PERIOD_NOT_SEQUENTIAL", "구간 시각이 순서대로 이어지지 않습니다.");
    private final String code;
    private final String message;
}
