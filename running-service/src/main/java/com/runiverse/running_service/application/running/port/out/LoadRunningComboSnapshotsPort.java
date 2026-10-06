package com.runiverse.running_service.application.running.port.out;

import java.util.List;

public interface LoadRunningComboSnapshotsPort {

    // 그 방 참가자 전원의 마지막 스냅샷. 판정은 방 전체를 한 번에 봐야 해서 방 단위로 읽는다.
    // 읽기에 실패하면 그대로 던진다 — 빈 목록으로 위장하면 비교 상대가 없어져
    // 살아 있던 콤보가 전부 끊긴 것처럼 화면에서 사라진다
    List<RunningComboSnapshot> loadSnapshots(Long runningRoomId);
}
