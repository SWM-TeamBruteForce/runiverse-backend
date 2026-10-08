package com.runiverse.running_service.application.running.port.out;

import com.runiverse.running_service.domain.common.vo.UserId;

import java.util.Optional;

public interface LoadLiveRunningStatusPort {

    // 비어 있으면 아직 한 번도 붙지 않은 참가자다 — 무엇으로 보여줄지는 쓰는 쪽이 정한다
    Optional<LiveRunningStatus> load(Long runningRoomId, UserId userId);
}
