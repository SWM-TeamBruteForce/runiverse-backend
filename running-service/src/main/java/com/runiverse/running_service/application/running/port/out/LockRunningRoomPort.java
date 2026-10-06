package com.runiverse.running_service.application.running.port.out;

import com.runiverse.running_service.domain.running.room.RunningRoom;
import com.runiverse.running_service.domain.running.room.vo.RunningRoomId;

import java.util.Optional;

public interface LockRunningRoomPort {

    // 시작이 rejoin()으로 인원을 늘리는 사이 취소가 leave()로 줄이면 인원이 어긋난다 —
    // loadById와 조건이 같고 잠그는 것만 다르다
    Optional<RunningRoom> lockById(RunningRoomId runningRoomId);
}
