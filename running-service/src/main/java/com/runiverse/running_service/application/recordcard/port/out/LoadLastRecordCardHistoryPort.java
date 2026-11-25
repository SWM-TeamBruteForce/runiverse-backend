package com.runiverse.running_service.application.recordcard.port.out;

import com.runiverse.running_service.domain.recordcard.vo.RecordCardId;

import java.util.Optional;

// 카드의 마지막 수정·삭제 이력 ID — 수정 초안이 만든 뒤 다른 수정이 끼어들었는지 가른다(이력이 없으면 비어 있다).
// record_cards.updated_at은 음악 완료에도 바뀌어 쓰지 않는다 — 음악 완료는 이력을 남기지 않는다
public interface LoadLastRecordCardHistoryPort {

    Optional<Long> loadLastId(RecordCardId recordCardId);
}
