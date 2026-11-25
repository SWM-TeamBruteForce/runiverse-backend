package com.runiverse.running_service.application.recordcard.query.detail;

import java.util.UUID;

// 8-1 — 보는 사람과 볼 카드
public record GetRecordCardDetailQuery(UUID viewerId, UUID recordCardId) {

}
