package com.runiverse.running_service.application.running.port.out;

public interface SaveRunningComboSnapshotPort {

    // 배치를 보낸 참가자의 것만 덮어쓴다 — 남의 스냅샷은 그 사람 배치가 갱신한다
    void saveSnapshot(Long runningRoomId, RunningComboSnapshot snapshot);
}
