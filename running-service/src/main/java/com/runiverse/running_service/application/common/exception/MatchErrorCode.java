package com.runiverse.running_service.application.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum MatchErrorCode implements ErrorCode {
    // 활성 신청이 이미 있다 — 대기·확정뿐 아니라 러닝 중도 여기 걸린다
    MATCH_ALREADY_IN_PROGRESS("MATCH_ALREADY_IN_PROGRESS", "이미 진행 중인 매칭이 있습니다."),
    // 제재 대상 이탈로 신청이 막힌 상태 — 해제 시각을 응답에 함께 담는 유일한 매칭 에러다
    MATCH_COOLDOWN("MATCH_COOLDOWN", "매칭 또는 러닝 중 이탈해 일정 시간 신청이 제한됩니다."),
    // 모집 마감(start_at - 오프셋)이 지난 슬롯 — 신청 시각으로 판정한다
    MATCH_SLOT_CLOSED("MATCH_SLOT_CLOSED", "이미 모집이 마감된 시간대입니다."),
    // 러닝이 시작된 뒤에는 이 버튼을 쓰지 않는다 — 종료는 WS RUNNING_FINISH가 맡는다
    MATCH_ALREADY_STARTED("MATCH_ALREADY_STARTED", "이미 시작된 러닝은 취소할 수 없습니다."),
    // 선택지(시각·거리)가 운영값이라 Request DTO가 못 보고 유스케이스가 본다 —
    // 클라에게는 요청 검증 실패와 같은 코드로 보인다. 실제 문구는 예외가 운영값으로 만든다
    INVALID_MATCH_OPTION("INVALID_REQUEST", "신청할 수 없는 시작 시각 또는 목표 거리입니다.");
    private final String code;
    private final String message;
}
