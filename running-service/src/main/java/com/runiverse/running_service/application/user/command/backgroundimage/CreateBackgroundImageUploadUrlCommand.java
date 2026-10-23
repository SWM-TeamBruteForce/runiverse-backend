package com.runiverse.running_service.application.user.command.backgroundimage;

import java.util.UUID;

public record CreateBackgroundImageUploadUrlCommand(
        UUID userId,
        String mimeType,
        long fileSizeBytes
) {

}
