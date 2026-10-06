package com.runiverse.running_service.application.running.port.out;

import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.running.player.RunningPlayer;
import com.runiverse.running_service.domain.running.room.vo.RunningRoomId;

import java.util.Optional;

public interface LoadRoomPlayerPort {

    // deleted_at과 무관하게 찾는다 — 이미 종료된 참가자도 찾아야 RUNNING_FINISH가 멱등이 된다.
    // 활성 신청만 보는 LoadActiveRunningPlayerPort와는 쓰임이 다르다
    Optional<RunningPlayer> load(RunningRoomId runningRoomId, UserId userId);
}
