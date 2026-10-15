package com.runiverse.running_service.application.running.port.out;

import com.runiverse.running_service.domain.common.vo.UserId;

// 트랙 필터가 볼 일시정지 표시 — 콤보 표시(MarkRunningComboPausedPort)와 별개다.
// 실패를 삼키지 않는다: 부르는 쪽이 로그를 남기고 이어 갈지 정한다
public interface MarkRunningTrackPausedPort {

    void markPaused(Long runningRoomId, UserId userId, long lastSequence);
}
