package com.runiverse.running_service.application.running.port.out;

import com.runiverse.running_service.domain.common.vo.UserId;

// 콤보 판정이 볼 일시정지 표시 — 화면 상태(LiveRunningStatus)와 별개다.
// 둘 다 실패를 삼키지 않는다: 부르는 쪽이 로그를 남기고 이어 갈지 정한다
public interface MarkRunningComboPausedPort {

    void markPaused(Long runningRoomId, UserId userId);

    // 관측이 다시 들어왔을 때 판정이 지운다 — 다시 달리는 중이 되는 것은 좌표로만이다
    void clearPaused(Long runningRoomId, UserId userId);
}
