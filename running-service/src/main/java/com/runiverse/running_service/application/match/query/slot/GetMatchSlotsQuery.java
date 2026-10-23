package com.runiverse.running_service.application.match.query.slot;

import java.time.LocalDate;

// 슬롯 목록은 누가 보든 같다 — 사람별 거절(제재·중복 신청)은 신청이 맡으므로 userId를 받지 않는다
public record GetMatchSlotsQuery(
        LocalDate date,
        int targetDistanceMeters
) {

}
