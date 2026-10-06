package com.runiverse.running_service.application.user.command.nickname;

import java.util.UUID;

public record ChangeNicknameCommand(
        UUID userId,
        String nickname
) {

}
