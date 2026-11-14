package com.runiverse.running_service.presentation.recordcard.response;

import com.runiverse.running_service.application.recordcard.command.completedraft.CompleteRecordCardDraftResult;

import java.util.UUID;

// 확정된 카드 ID — 앱은 이 값으로 상세(8-1)를 연다
public record CompleteRecordCardDraftResponse(UUID recordCardId) {

    public static CompleteRecordCardDraftResponse from(CompleteRecordCardDraftResult result) {
        return new CompleteRecordCardDraftResponse(result.recordCardId());
    }
}
