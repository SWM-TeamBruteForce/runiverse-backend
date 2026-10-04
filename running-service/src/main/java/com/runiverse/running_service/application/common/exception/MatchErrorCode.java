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
    MATCH_ALREADY_STARTED("MATCH_ALREADY_STARTED", "이미 시작된 러닝은 취소할 수 없습니다.");
    private final String code;
    private final String message;
}
