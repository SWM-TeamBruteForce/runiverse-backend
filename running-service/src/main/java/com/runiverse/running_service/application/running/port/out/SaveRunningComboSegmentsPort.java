package com.runiverse.running_service.application.running.port.out;

import java.util.List;

public interface SaveRunningComboSegmentsPort {

    // 같은 구간(쌍·시작 시각)을 다시 저장하면 덮어쓴다 — 두 인스턴스가 같은 관계를 함께 끊어도 한 칸이다.
    // 실패를 삼키지 않는다: 구간을 못 남긴 채 관계가 끊긴 상태로 저장되면 그 구간은 되살릴 수 없다
    void saveSegments(Long runningRoomId, List<RunningComboSegment> segments);
}
