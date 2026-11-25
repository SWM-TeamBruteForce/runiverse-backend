package com.runiverse.running_service.application.recordcard.command.update;

import com.runiverse.running_service.application.recordcard.command.createdraft.CreateRecordCardDraftResult;
import com.runiverse.running_service.application.recordcard.query.detail.GetRecordCardDetailResult;

// 9-3 — recordCard는 늘 있다. draft가 null이면 이미 반영됐고(recordCard = 수정 뒤),
// 있으면 새 사진을 올려 9-2로 확정해야 반영된다(recordCard = 지금, 수정 전)
public record UpdateRecordCardResult(GetRecordCardDetailResult recordCard, CreateRecordCardDraftResult draft) {

}
