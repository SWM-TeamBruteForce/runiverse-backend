package com.runiverse.running_service.application.recordcard.port.out;

import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.recordcard.vo.RecordCardId;

public interface ExistsOwnRecordCardPort {

    // 이 사람의 카드인지 — 없거나 남의 카드면 false(모두 404). 카드 주인은 바뀌지 않아 잠그기 전에 봐도 된다
    boolean existsOwnedBy(RecordCardId recordCardId, UserId userId);
}
