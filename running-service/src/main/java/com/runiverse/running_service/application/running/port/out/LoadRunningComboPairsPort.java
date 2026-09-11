package com.runiverse.running_service.application.running.port.out;

import java.util.List;

public interface LoadRunningComboPairsPort {

    // 그 방의 관계 상태 전부. 아직 한 번도 겹치지 않은 관계는 담기지 않는다.
    // 읽기에 실패하면 그대로 던진다 — 빈 목록으로 위장하면 쌓여 있던 콤보와
    // 최고 기록이 이번 배치의 저장으로 1부터 다시 시작한다
    List<RunningComboPair> loadPairs(Long runningRoomId);
}
