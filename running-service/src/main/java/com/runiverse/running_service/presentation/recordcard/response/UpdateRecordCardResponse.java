package com.runiverse.running_service.presentation.recordcard.response;

import com.runiverse.running_service.application.recordcard.command.update.UpdateRecordCardResult;

// 9-3 — 늘 200이다. draft가 null이면 이미 반영됐고 recordCard가 수정 뒤다.
// draft가 있으면 recordCard는 지금(수정 전) 모습이다 — 새 사진을 올리고 9-2로 확정한 뒤 8-1로 수정 결과를 받는다
public record UpdateRecordCardResponse(RecordCardDetailResponse recordCard, CreateRecordCardDraftResponse draft) {

    public static UpdateRecordCardResponse from(UpdateRecordCardResult result) {
        return new UpdateRecordCardResponse(RecordCardDetailResponse.from(result.recordCard()),
                result.draft() == null ? null : CreateRecordCardDraftResponse.from(result.draft()));
    }
}
