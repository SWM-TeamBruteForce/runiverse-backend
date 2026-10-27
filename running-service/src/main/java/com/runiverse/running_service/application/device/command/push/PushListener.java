package com.runiverse.running_service.application.device.command.push;

import com.runiverse.running_service.application.device.port.in.SendPushUsecase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

// 실패해도 원래 요청은 이미 커밋됐다(예외는 Spring이 로그만 남긴다) — 푸시 하나를 잃을 뿐 되돌리지 않는다
@Component
@RequiredArgsConstructor
public class PushListener {

    private final SendPushUsecase sendPushUsecase;

    // 커밋 뒤에는 원래 트랜잭션에 합류하면 반영되지 않는다 — 만료 토큰 정리를 위해 새 트랜잭션을 연다
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void send(PushRequestedEvent event) {
        sendPushUsecase.handle(event.command());
    }
}
