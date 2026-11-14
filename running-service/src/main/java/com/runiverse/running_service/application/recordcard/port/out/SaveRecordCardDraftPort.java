package com.runiverse.running_service.application.recordcard.port.out;

import java.time.Duration;

public interface SaveRecordCardDraftPort {

    // 유효 시간이 지나면 저장소가 지운다 — 확정하지 않고 떠난 초안은 아무것도 남기지 않는다
    void save(RecordCardDraft draft, Duration ttl);
}
