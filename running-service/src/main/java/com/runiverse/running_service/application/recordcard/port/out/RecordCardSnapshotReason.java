package com.runiverse.running_service.application.recordcard.port.out;

// 이력(record_card_histories)을 남기는 이유 — 유스케이스가 저장 포트에 알린다. 카드(도메인)는 자기 이력을 모른다
public enum RecordCardSnapshotReason {
    // 수정을 반영하기 직전 — 9-3 바로 반영, 9-2 수정 확정
    UPDATE,
    // 지우기 직전 — 9-4
    DELETE
}
