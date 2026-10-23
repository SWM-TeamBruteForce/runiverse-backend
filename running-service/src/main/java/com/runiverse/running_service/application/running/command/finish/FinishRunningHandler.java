package com.runiverse.running_service.application.running.command.finish;

import com.runiverse.running_service.application.running.common.GoalCheck;
import com.runiverse.running_service.application.running.common.RunningFinisher;
import com.runiverse.running_service.application.running.port.in.FinishRunningUsecase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class FinishRunningHandler implements FinishRunningUsecase {

    private final RunningFinisher runningFinisher;

    // forced는 조기 종료 '의사'일 뿐 최종 상태를 정하지 않는다 — 확정 거리로만 판정한다.
    // 다만 forced=false(다 뛰었다고 본 종료)는 확정 거리가 모자라면 확정하지 않고 남은 거리를 돌려준다.
    // 트랜잭션 경계는 RunningFinisher다
    @Override
    public GoalCheck handle(FinishRunningCommand command) {
        return runningFinisher.finish(command.runningRoomId(), command.userId(), command.forced());
    }
}
