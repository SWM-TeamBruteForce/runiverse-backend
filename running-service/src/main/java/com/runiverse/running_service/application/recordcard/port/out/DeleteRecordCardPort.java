package com.runiverse.running_service.application.recordcard.port.out;

import com.runiverse.running_service.domain.recordcard.vo.RecordCardId;

// 카드를 하드 삭제한다 — 사진 행은 FK CASCADE로 함께 지워진다. S3 객체(사진·음악)는 지우지 않는다.
// 기록 행을 잠그고 이력을 남긴 같은 트랜잭션에서 부른다
public interface DeleteRecordCardPort {

    void delete(RecordCardId recordCardId);
}
