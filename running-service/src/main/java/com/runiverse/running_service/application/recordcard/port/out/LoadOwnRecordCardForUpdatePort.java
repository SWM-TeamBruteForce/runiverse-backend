package com.runiverse.running_service.application.recordcard.port.out;

import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.recordcard.RecordCard;
import com.runiverse.running_service.domain.recordcard.vo.RecordCardId;

import java.util.Optional;

public interface LoadOwnRecordCardForUpdatePort {

    // 이 사람 카드면 기록 행을 FOR UPDATE로 잠근 뒤 카드와 사진을 읽는다. 없거나 남의 카드면 잠그지 않고 비어 있다(모두 404)
    Optional<RecordCard> loadOwnedForUpdate(RecordCardId recordCardId, UserId userId);
}
