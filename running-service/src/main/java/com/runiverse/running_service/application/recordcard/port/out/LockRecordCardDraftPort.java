package com.runiverse.running_service.application.recordcard.port.out;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

public interface LockRecordCardDraftPort {

    // 같은 초안의 확정을 하나만 처리한다 — 잡으면 잠금 토큰, 이미 누가 잡고 있으면 비어 있다(기다리지 않는다).
    // 유효 시간이 지나면 저절로 풀린다 — 잡은 서버가 죽어도 잠금이 남지 않는다
    Optional<String> tryLock(UUID recordCardDraftId, Duration ttl);

    // 내 토큰일 때만 푼다 — 유효 시간이 지나 다른 요청이 새로 잡은 잠금을 지우지 않는다
    void unlock(UUID recordCardDraftId, String token);
}
