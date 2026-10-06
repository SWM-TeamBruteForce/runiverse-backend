package com.runiverse.running_service.presentation.user.response;

import java.util.UUID;

public record UserProfileResponse(
        UUID userId,
        boolean isMe,
        String nickname,
        String profileImageUrl,
        String introduction,
        long friendCount,
        String friendStatus
) {

}
