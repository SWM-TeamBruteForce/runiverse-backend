package com.runiverse.running_service.application.recordcard.command.completedraft;

import java.util.UUID;

// 확정된 카드의 ID만 준다 — 앱은 9-1에서 초안 ID만 받아 카드 ID를 모른다. 화면은 이 값으로 상세(8-1)를 연다.
// 같은 초안으로 다시 불러도 같은 값이다
public record CompleteRecordCardDraftResult(UUID recordCardId) {

}
