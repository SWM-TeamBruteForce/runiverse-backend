package com.runiverse.running_service.application.user.command.backgroundimage;

public record CreateBackgroundImageUploadUrlResult(
        String backgroundImageKey,
        String uploadUrl
) {

}
