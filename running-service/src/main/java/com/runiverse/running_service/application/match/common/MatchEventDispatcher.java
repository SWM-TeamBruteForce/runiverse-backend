package com.runiverse.running_service.application.match.common;

import com.runiverse.running_service.application.match.port.out.PublishMatchEventPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class MatchEventDispatcher {

    private final PublishMatchEventPort publishMatchEventPort;

    // 커밋 후에 내보낸다 — 커밋 전에 쏘면 롤백된 매칭을 알리거나,
    // 다른 인스턴스가 아직 보이지 않는 상태를 받게 된다
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void dispatch(MatchRoomChangedEvent event) {
        publishMatchEventPort.publish(event.event());
    }
}
