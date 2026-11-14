package com.runiverse.running_service.application.recordcard.port.out;

import java.util.Optional;
import java.util.UUID;

public interface LoadRecordCardDraftPort {

    // 초안을 읽는다 — 없거나 유효 시간이 지났으면 비어 있다(둘을 가르지 않는다, 모두 404).
    // 남의 초안인지는 application이 초안의 userId로 대조한다
    Optional<RecordCardDraft> load(UUID recordCardDraftId);
}
