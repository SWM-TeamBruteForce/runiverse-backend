package com.runiverse.running_service.application.running.query.status;

// 유저가 지금 무엇을 하는 중인가. 저장하지 않고 활성 신청·방 상태·모집 마감 시각으로 계산한다.
// 값마다 클라이언트의 다음 동작이 하나씩 대응된다 — 그래서 방 상태(5종)를 그대로 쓰지 않는다
public enum UserRunningStatus {
    // 활성 신청이 없다
    IDLE,
    // 매칭을 기다리는 중 — 스트림에 연결한다
    WAITING,
    // 확정됐고 시작을 기다리는 중 — 매칭이면 카운트다운, 솔로면 곧바로 러닝
    READY,
    // 달리는 중 — WS에 붙어 RUNNING_START를 다시 보낸다
    RUNNING
}
