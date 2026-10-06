package com.runiverse.running_service.application.user.command.accountdeletion;

import com.runiverse.running_service.application.user.port.out.UnlinkKakaoPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class KakaoUnlinkListener {

    private final UnlinkKakaoPort unlinkKakaoPort;

    // 실패해도 되돌리지 않는다 — 남는 피해는 카카오 앱 목록에 이름이 남는 것이고,
    // 우리 DB엔 흔적이 없어 그 연결로는 아무것도 할 수 없다
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void unlink(KakaoUnlinkRequestedEvent event) {
        unlinkKakaoPort.unlink(event.providerId());
    }
}
