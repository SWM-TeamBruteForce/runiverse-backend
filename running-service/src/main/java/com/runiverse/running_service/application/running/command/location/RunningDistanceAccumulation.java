package com.runiverse.running_service.application.running.command.location;

import com.runiverse.running_service.application.running.port.out.RunningDistance;

// 배치 하나를 누적에 반영한 결과 — 누적 거리와 함께, 콤보가 참가자 상태를 정할 재료를 낸다
public record RunningDistanceAccumulation(
        RunningDistance distance,
        // 정확도 상한 이하의 새 좌표가 하나라도 있었나 — 없으면 이 배치는 관측이 아니다(터널·재전송분)
        boolean observed,
        // 의심점이 반경 밖으로 옮겨졌나 — 옮겨졌으면 분명히 움직였다
        boolean moved,
        // 반영한 좌표 사이에 일시정지 경계가 있었나 — 일시정지 신호가 유실돼도 이 판정에서 콤보를 끊는다
        boolean pauseCrossed
) {

}
