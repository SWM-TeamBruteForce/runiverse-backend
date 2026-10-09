package com.runiverse.running_service.presentation.running.websocket.message;

// 러닝 중 누적은 목표를 넘었지만 확정 거리가 모자란 동안 본인에게만 나간다.
// 클라는 이 값으로 "N m 더"를 보여주고, RUNNING_FINISHED가 올 때까지 계속 보낸다
public record RunningGoalPendingPayload(int remainingMeters) {

}
