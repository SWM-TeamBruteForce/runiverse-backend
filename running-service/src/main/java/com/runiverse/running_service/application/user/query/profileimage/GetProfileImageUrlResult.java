package com.runiverse.running_service.application.user.query.profileimage;

import java.util.UUID;

public record GetProfileImageUrlResult(
        UUID userId,
        String profileImageUrl
) {

}
