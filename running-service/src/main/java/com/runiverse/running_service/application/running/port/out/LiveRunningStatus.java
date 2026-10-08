package com.runiverse.running_service.application.running.port.out;

public enum LiveRunningStatus {
    RUNNING,
    PAUSED,
    DISCONNECTED,
    FINISHED;

    // previous가 null이면 아직 상태가 없는 참가자다
    public boolean canChangeFrom(LiveRunningStatus previous) {
        // 끝난 뒤 닫히는 연결이 FINISHED를 덮으면 안 된다
        if (previous == FINISHED) {
            return false;
        }
        // 끊김은 붙어 있던 참가자에게만 일어난다 — 한 번도 붙지 않은 참가자는 상태가 없는 그대로 둔다
        if (this == DISCONNECTED) {
            return previous == RUNNING || previous == PAUSED;
        }
        // 좌표·PAUSE·RESUME이 왔다는 것 자체가 연결돼 있다는 뜻이다 — DISCONNECTED에서도 넘어간다.
        // 재연결 뒤 옛 연결의 끊김이 늦게 반영돼도 다음 메시지가 바로잡는다
        return true;
    }
}
