package com.runiverse.running_service.application.recordcard.port.out;

public interface UpdateRecordCardDraftPort {

    // 초안을 덮어쓴다 — 남은 유효 시간은 그대로 둔다(늘리지 않는다). 합친 사진 표시·확정 표시가 이 길로 온다.
    // 그사이 유효 시간이 지나 초안이 없으면 새로 만들지 않고 false
    boolean update(RecordCardDraft draft);
}
