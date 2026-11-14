package com.runiverse.running_service.application.recordcard.command.completedraft;

import java.util.UUID;

// 9-2 확정 — 본문이 없다. 확정할 내용은 모두 초안에 있다
public record CompleteRecordCardDraftCommand(
        UUID userId,                // 토큰 주인 — 초안을 만든 사람과 다르면 404
        UUID recordCardDraftId      // 9-1이 준 초안 ID
) {

}
