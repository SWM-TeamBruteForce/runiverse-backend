package com.runiverse.running_service.integration_test.fake;

import com.runiverse.running_service.application.user.port.out.ClearProfileImagePort;
import com.runiverse.running_service.application.user.port.out.UpdateProfileImagePort;
import com.runiverse.running_service.domain.user.vo.ProfileImageKey;
import com.runiverse.running_service.domain.common.vo.UserId;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class InMemoryProfileImageStore implements UpdateProfileImagePort, ClearProfileImagePort {

    private final Map<UUID, String> saved = new HashMap<>();

    @Override
    public void updateProfileImage(UserId userId, ProfileImageKey profileImageKey) {
        saved.put(userId.value(), profileImageKey.value());
    }

    @Override
    public void clearProfileImage(UserId userId) {
        // 실제 어댑터가 profile_image_key를 null로 만드는 것과 같은 상태다
        saved.remove(userId.value());
    }

    // 아래는 검증 전용
    public Optional<String> keyOf(UUID userId) {
        return Optional.ofNullable(saved.get(userId));
    }

    public boolean isEmpty() {
        return saved.isEmpty();
    }
}
