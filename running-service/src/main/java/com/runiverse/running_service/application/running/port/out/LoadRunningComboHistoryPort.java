package com.runiverse.running_service.application.running.port.out;

public interface LoadRunningComboHistoryPort {

    // 방 하나치를 통째로 읽는다 — 이어지던 관계(openPairs)는 시작 시각이 있는 것만 담는다
    RunningComboHistory loadHistory(Long runningRoomId);
}
