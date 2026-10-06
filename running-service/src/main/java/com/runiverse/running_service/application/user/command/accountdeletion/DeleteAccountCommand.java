package com.runiverse.running_service.application.user.command.accountdeletion;

import java.util.UUID;

public record DeleteAccountCommand(
        UUID userId,
        String accessTokenId
) {

}
