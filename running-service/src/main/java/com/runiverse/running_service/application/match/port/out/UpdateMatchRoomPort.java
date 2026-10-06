package com.runiverse.running_service.application.match.port.out;

import com.runiverse.running_service.domain.running.room.RunningRoom;

public interface UpdateMatchRoomPort {

    // 합류로 바뀐 인원·평균 페이스·세션을 반영한다
    void update(RunningRoom room);
}
