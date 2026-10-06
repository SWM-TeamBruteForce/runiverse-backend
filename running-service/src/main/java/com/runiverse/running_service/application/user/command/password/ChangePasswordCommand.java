package com.runiverse.running_service.application.user.command.password;

import java.util.UUID;

public record ChangePasswordCommand(
        UUID userId,
        String currentPassword,
        String newPassword
) {

}
