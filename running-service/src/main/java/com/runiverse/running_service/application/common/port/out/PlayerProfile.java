package com.runiverse.running_service.application.common.port.out;

import java.util.UUID;

public record PlayerProfile(
        UUID userId, String nickname, String profileImageKey, String introduction
) {

}
