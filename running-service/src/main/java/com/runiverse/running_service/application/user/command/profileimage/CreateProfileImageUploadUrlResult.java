package com.runiverse.running_service.application.user.command.profileimage;

public record CreateProfileImageUploadUrlResult(
        String profileImageKey,
        String uploadUrl
) {

}
