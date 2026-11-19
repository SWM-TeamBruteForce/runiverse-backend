package com.runiverse.running_service.application.recordcard.port.out;

import com.runiverse.running_service.domain.recordcard.RecordCard;
import com.runiverse.running_service.domain.recordcard.vo.RecordCardId;

import java.util.Optional;

public interface LoadRecordCardForUpdatePort {

    // 카드를 고치기 전에 읽는다 — 카드가 붙은 기록 행을 FOR UPDATE로 잠근 뒤 카드와 사진을 읽는다.
    // 카드 쓰기는 모두 기록 행 하나로 줄을 세운다(작성·수정·삭제·음악 완료) — 카드 행을 따로 잠그지 않는다.
    // 카드가 없으면(지운 카드) 비어 있다. 트랜잭션 안에서 불러야 잠금이 유지된다
    Optional<RecordCard> loadForUpdate(RecordCardId recordCardId);
}
