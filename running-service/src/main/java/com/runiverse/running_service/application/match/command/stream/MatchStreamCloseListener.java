package com.runiverse.running_service.application.match.command.stream;

import com.runiverse.running_service.application.match.port.out.MatchRoomMembershipPort;
import com.runiverse.running_service.application.match.port.out.MatchStreamConnection;
import com.runiverse.running_service.application.match.port.out.MatchStreamPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class MatchStreamCloseListener {

    private final MatchStreamPort matchStreamPort;
    private final MatchRoomMembershipPort matchRoomMembershipPort;

    // 명부에서 빼야 이벤트가 끊기고, 연결을 닫아야 keep-alive도 멈춘다.
    // 이 인스턴스에 붙은 연결만 닫힌다 — 다른 서버의 연결은 스스로 만료된다
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void close(MatchStreamCloseRequestedEvent event) {
        matchStreamPort.find(event.userId())
                .ifPresent(MatchStreamConnection::closeForAccountDeletion);
        matchRoomMembershipPort.leave(event.userId());
    }
}
