package com.runiverse.running_service.application.running.port.out;

import java.time.LocalDateTime;

// 응답 최상위의 세 값. 참가자별이 아니라 조회하는 본인 기록 하나에서만 나온다(api-spec 6-1).
// 폴리라인은 500점짜리 text라 참가자 수만큼 끌어오면 그대로 낭비다
public record RunningResultRecord(
        String routePolyline,
        LocalDateTime startedAt,
        LocalDateTime finishedAt,
        // 아래 둘은 6-2만 쓴다 — 같은 행에서 오는 값이라 따로 조회하지 않는다
        int totalDistanceMeters,
        Integer totalElevationGainMeters
) {

}
