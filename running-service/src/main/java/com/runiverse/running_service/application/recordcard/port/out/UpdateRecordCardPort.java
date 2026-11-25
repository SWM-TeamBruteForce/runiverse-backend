package com.runiverse.running_service.application.recordcard.port.out;

import com.runiverse.running_service.domain.recordcard.RecordCard;

// 수정(9-3)이 바꾼 카드를 저장한다 — 글·활동유형·공개 범위·썸네일, 빠진 사진 삭제·남긴 사진 순서·설명·음악 상태, 새 사진 추가.
// 기록 행을 잠그고 읽은 카드를 도메인에서 고친 뒤, 이력을 남기고 나서 부른다(같은 트랜잭션)
public interface UpdateRecordCardPort {

    void update(RecordCard recordCard);
}
