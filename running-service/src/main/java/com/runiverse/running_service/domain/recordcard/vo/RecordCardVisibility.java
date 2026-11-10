package com.runiverse.running_service.domain.recordcard.vo;

// 카드별 공개 범위 — 기본값은 두지 않는다(작성 화면이 고른다)
public enum RecordCardVisibility {
    // 작성자와 친구인 사람만
    FRIENDS,
    // 누구나
    PUBLIC,
    // 작성자만
    PRIVATE
}
