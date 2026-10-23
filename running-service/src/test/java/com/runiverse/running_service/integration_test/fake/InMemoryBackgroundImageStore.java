package com.runiverse.running_service.integration_test.fake;

import com.runiverse.running_service.application.user.port.out.ClearBackgroundImagePort;
import com.runiverse.running_service.application.user.port.out.UpdateBackgroundImagePort;
import com.runiverse.running_service.domain.user.vo.BackgroundImageKey;
import com.runiverse.running_service.domain.common.vo.UserId;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class InMemoryBackgroundImageStore implements UpdateBackgroundImagePort, ClearBackgroundImagePort {

    private final Map<UUID, String> saved = new HashMap<>();

    @Override
    public void updateBackgroundImage(UserId userId, BackgroundImageKey backgroundImageKey) {
        saved.put(userId.value(), backgroundImageKey.value());
    }

    @Override
    public void clearBackgroundImage(UserId userId) {
        // 실제 어댑터가 background_image_key를 null로 만드는 것과 같은 상태다
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
