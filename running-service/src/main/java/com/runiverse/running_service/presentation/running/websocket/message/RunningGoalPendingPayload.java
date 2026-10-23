package com.runiverse.running_service.presentation.running.websocket.message;

// RUNNING_FINISH(forced=false)의 확정 거리가 목표에 못 미칠 때 그 응답으로 본인에게만 나간다.
// 클라는 이 값으로 "N m 더"를 보여주고, RUNNING_FINISHED가 올 때까지 좌표를 계속 보낸다
public record RunningGoalPendingPayload(int remainingMeters) {

}
