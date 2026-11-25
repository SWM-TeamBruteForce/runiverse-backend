package com.runiverse.running_service.application.recordcard.command.retrymusic;

import java.util.UUID;

public record RetryRecordCardMusicCommand(UUID userId, UUID recordCardId) {

}
