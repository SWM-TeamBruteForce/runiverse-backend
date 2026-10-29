package com.runiverse.running_service.application.device.command.push;

import com.runiverse.running_service.application.device.port.in.SendPushUsecase;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

// 실패해도 원래 요청은 이미 커밋됐다(예외는 Spring이 로그만 남긴다) — 푸시 하나를 잃을 뿐 되돌리지 않는다.
// 트랜잭션 안에서 발행해야 돈다 — 밖에서 발행하면 오류 없이 무시된다
@Component
@RequiredArgsConstructor
public class PushListener {

    private final SendPushUsecase sendPushUsecase;

    // FCM 응답을 요청·예약 스레드가 기다리지 않게 발송 스레드로 넘긴다.
    // 트랜잭션은 열지 않는다 — 발송 유스케이스가 DB를 쓰는 동안만 짧게 연다
    @Async("pushExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void send(PushRequestedEvent event) {
        sendPushUsecase.handle(new SendPushCommand(event.userId(), event.message()));
    }
}
