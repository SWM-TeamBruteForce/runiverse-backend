package com.runiverse.running_service.application.relationship.port.out;

import java.util.UUID;

public record RecommendationCandidate(
        UUID userId,
        String nickname,
        String profileImageKey      // 사진이 없으면 null
) {

}
