package com.runiverse.running_service.application.match.port.out;

import com.runiverse.running_service.domain.running.room.RunningRoom;
import com.runiverse.running_service.domain.running.room.vo.RunningRoomId;

import java.util.Optional;

public interface LoadMatchRoomDetailPort {

    // 잠그지 않고 방을 읽는다 — 스냅샷 조회가 신청·취소와 경합하면 안 된다.
    // 세션까지 함께 복원한다(방 애그리거트는 세션 없이는 판정할 수 없다)
    Optional<RunningRoom> loadDetailById(RunningRoomId runningRoomId);
}
