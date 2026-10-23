package com.runiverse.running_service.application.running.port.out;

import java.util.List;

public interface LoadRecordedRoutesPort {

    // 방 안에서 기록을 남긴 참가자의 경로만 담는다 — 기록이 없는 참가자는 목록에 없다
    List<RecordedRoute> loadRoutes(Long runningRoomId);
}
