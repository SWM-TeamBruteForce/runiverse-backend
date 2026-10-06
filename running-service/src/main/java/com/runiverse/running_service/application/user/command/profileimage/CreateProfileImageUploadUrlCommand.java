package com.runiverse.running_service.application.user.command.profileimage;

import java.util.UUID;

public record CreateProfileImageUploadUrlCommand(
        UUID userId,
        String mimeType,
        long fileSizeBytes
) {

}
