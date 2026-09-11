package com.runiverse.running_service.application.running.port.out;

import java.util.List;

public interface SaveRunningComboPairsPort {

    // 배치를 보낸 참가자가 낀 관계만 넘긴다 — 방 전체를 넘기면 그 사이 다른 인스턴스가
    // 갱신한 남의 관계를 내가 읽은 옛 값으로 되돌린다.
    // 저장에 실패해도 던지지 않는다: 다음 배치가 이전 상태에서 이어 판정한다
    void savePairs(Long runningRoomId, List<RunningComboPair> pairs);
}
