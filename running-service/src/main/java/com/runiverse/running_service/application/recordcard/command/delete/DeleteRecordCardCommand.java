package com.runiverse.running_service.application.recordcard.command.delete;

import java.util.UUID;

// 9-4 — 지우는 사람과 지울 카드
public record DeleteRecordCardCommand(UUID userId, UUID recordCardId) {

}
