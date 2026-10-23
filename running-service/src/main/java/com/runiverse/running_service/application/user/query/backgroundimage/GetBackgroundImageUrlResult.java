package com.runiverse.running_service.application.user.query.backgroundimage;

import java.util.UUID;

public record GetBackgroundImageUrlResult(
        UUID userId,
        String backgroundImageUrl
) {

}
