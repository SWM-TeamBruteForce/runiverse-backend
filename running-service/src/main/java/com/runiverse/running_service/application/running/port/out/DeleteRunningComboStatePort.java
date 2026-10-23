package com.runiverse.running_service.application.running.port.out;

public interface DeleteRunningComboStatePort {

    // 방의 콤보 상태(스냅샷·관계·구간)를 모두 지운다. 실패해도 던지지 않는다 — TTL이 결국 지운다
    void deleteComboState(Long runningRoomId);
}
