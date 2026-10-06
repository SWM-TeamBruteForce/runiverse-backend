package com.runiverse.running_service.application.running.port.out;

public interface PublishRunningProgressPort {

    // 방 채널로 내보낸다 — 참가자를 든 인스턴스들이 받아 각자의 로컬 연결에 밀어 넣는다.
    // 실패해도 던지지 않는다: 진행 표시는 10초 뒤 다음 배치에 다시 나간다
    void publish(Long runningRoomId, RunningProgress progress);
}
