package com.runiverse.running_service.domain.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum RunningMetricErrorCode implements ErrorCode {
    DISTANCE_OUT_OF_RANGE("DISTANCE_OUT_OF_RANGE", "거리는 1m 이상 500km 이하여야 합니다."),
    PACE_OUT_OF_RANGE("PACE_OUT_OF_RANGE", "페이스는 1km당 120초 이상 3600초 이하여야 합니다."),
    SPLIT_PACE_OUT_OF_RANGE("SPLIT_PACE_OUT_OF_RANGE", "구간 페이스는 1km당 1초 이상이어야 합니다."),
    ELAPSED_TIME_OUT_OF_RANGE("ELAPSED_TIME_OUT_OF_RANGE", "소요 시간은 1초 이상 24시간 이하여야 합니다."),
    CALORIES_REQUIRED("CALORIES_REQUIRED", "칼로리는 필수입니다."),
    RUNNING_PERIOD_REQUIRED("RUNNING_PERIOD_REQUIRED", "시작·종료 시각은 필수입니다."),
    INVALID_RUNNING_PERIOD("INVALID_RUNNING_PERIOD", "종료 시각은 시작 시각보다 뒤여야 합니다."),
    CADENCE_OUT_OF_RANGE("CADENCE_OUT_OF_RANGE", "케이던스는 분당 1보 이상 300보 이하여야 합니다."),
    CALORIES_OUT_OF_RANGE("CALORIES_OUT_OF_RANGE", "칼로리는 0 이상 20000 이하여야 합니다."),
    ELEVATION_GAIN_OUT_OF_RANGE("ELEVATION_GAIN_OUT_OF_RANGE", "누적 상승 고도는 0m 이상 20000m 이하여야 합니다."),
    ELEVATION_CHANGE_OUT_OF_RANGE("ELEVATION_CHANGE_OUT_OF_RANGE", "고도 변화는 -10000m 이상 10000m 이하여야 합니다."),
    WEATHER_CODE_REQUIRED("WEATHER_CODE_REQUIRED", "날씨 코드는 필수입니다."),
    WEATHER_CODE_OUT_OF_RANGE("WEATHER_CODE_OUT_OF_RANGE", "날씨 코드는 0 이상 99 이하여야 합니다."),
    TEMPERATURE_REQUIRED("TEMPERATURE_REQUIRED", "기온은 null일 수 없습니다."),
    TEMPERATURE_OUT_OF_RANGE("TEMPERATURE_OUT_OF_RANGE", "기온은 -99.9도 이상 99.9도 이하여야 합니다.");
    private final String code;
    private final String message;
}
