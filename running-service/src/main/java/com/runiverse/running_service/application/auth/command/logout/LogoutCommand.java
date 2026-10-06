package com.runiverse.running_service.application.auth.command.logout;

import java.util.UUID;

public record LogoutCommand(
        UUID userId,
        String accessTokenId
) {

}
