package com.runiverse.running_service.application.running.port.out;

import java.time.LocalDateTime;

// 구간의 시작 경계점은 startAt, 끝 경계점은 endAt 시각에 지났다.
// 경로 범위는 segmentNumber 조각 안의 점 번호다
public record RecordedSplitTime(
        int segmentNumber,
        int routeStartIndex,
        int routeEndIndex,
        LocalDateTime startAt,
        LocalDateTime endAt
) {

}
