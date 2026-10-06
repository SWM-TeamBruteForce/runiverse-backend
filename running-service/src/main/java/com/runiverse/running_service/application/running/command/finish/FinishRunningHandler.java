package com.runiverse.running_service.application.running.command.finish;

import com.runiverse.running_service.application.running.common.RunningFinisher;
import com.runiverse.running_service.application.running.port.in.FinishRunningUsecase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class FinishRunningHandler implements FinishRunningUsecase {

    private final RunningFinisher runningFinisher;

    // forced는 조기 종료 '의사'일 뿐 최종 상태를 정하지 않는다 — 확정 거리로만 판정한다.
    // 트랜잭션 경계는 RunningFinisher다
    @Override
    public void handle(FinishRunningCommand command) {
        runningFinisher.finish(command.runningRoomId(), command.userId());
    }
}
