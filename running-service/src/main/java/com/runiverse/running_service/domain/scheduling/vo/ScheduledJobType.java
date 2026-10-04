package com.runiverse.running_service.domain.scheduling.vo;

// 예약된 그 시각에 무엇을 할 것인가
public enum ScheduledJobType {
    MATCH_CLOSE,
    // 방 상태를 바꾸지 않는 유일한 타입 — 알리기만 하고 STARTED 전이는 RUNNING_START가 한다
    RUNNING_READY,
    // 시작 시각 정각 — 방만 STARTED로 올린다.
    // 참가자 상태는 올리지 않는다. 그건 각자가 보낸 시작 메시지의 몫이다
    RUNNING_START,
    // 시작 시각 + 유예 — 그때까지 닫히지 않은 방과 남은 참가자를 서버가 닫는다
    RUNNING_FORCE_FINISH
}
