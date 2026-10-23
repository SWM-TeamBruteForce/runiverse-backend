package com.runiverse.running_service.application.running.port.out;

import com.runiverse.running_service.domain.common.vo.UserId;

// 콤보 판정이 볼 종료 표시 — 일시정지와 달리 지우는 경로가 없다. 방이 닫힐 때 콤보 상태와 함께 사라진다.
// 실패를 삼키지 않는다: 부르는 쪽이 로그를 남기고 이어 간다
public interface MarkRunningComboFinishedPort {

    void markFinished(Long runningRoomId, UserId userId);
}
