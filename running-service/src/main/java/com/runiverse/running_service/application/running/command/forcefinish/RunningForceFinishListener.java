package com.runiverse.running_service.application.running.command.forcefinish;

import com.runiverse.running_service.application.running.port.in.ForceFinishRunningRoomUsecase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

// 방이 닫힌 뒤 남은 미출석자를 예약 시각까지 기다리지 않고 바로 닫는다 — 그동안 다음 신청이 막힌다.
// 실패해도 종료 요청은 이미 커밋됐고(예외는 Spring이 로그만 남긴다), 예약된 강제 종료가 다시 닫는다
@Component
@RequiredArgsConstructor
public class RunningForceFinishListener {

    private final ForceFinishRunningRoomUsecase forceFinishRunningRoomUsecase;

    // 커밋 뒤에는 원래 트랜잭션에 합류하면 반영되지 않는다 — 새 트랜잭션을 연다
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void forceFinish(RunningForceFinishRequestedEvent event) {
        forceFinishRunningRoomUsecase.handle(new ForceFinishRunningRoomCommand(event.runningRoomId()));
    }
}
