package com.runiverse.running_service.application.running.command.session;

import com.runiverse.running_service.application.running.port.out.RunningConnection;
import com.runiverse.running_service.application.running.port.out.RunningSessionPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class RunningConnectionCloseListener {

    private final RunningSessionPort runningSessionPort;

    // 인증은 핸드셰이크에서만 하므로, 열려 있는 연결은 탈퇴 뒤에도 좌표를 받아 같은 방에 진행을 퍼뜨린다.
    // 이 인스턴스에 붙은 연결만 닫힌다 — 다른 서버의 연결은 닫지 못한다. 매칭 스트림과 같은 한계다
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void close(RunningConnectionCloseRequestedEvent event) {
        runningSessionPort.find(event.userId())
                .ifPresent(RunningConnection::closeForAccountDeletion);
    }
}
