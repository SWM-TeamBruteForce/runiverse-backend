package com.runiverse.running_service.application.user.command.profileimage;

import java.util.UUID;

public record ChangeProfileImageResult(
        UUID userId,
        String profileImageKey
) {

}
