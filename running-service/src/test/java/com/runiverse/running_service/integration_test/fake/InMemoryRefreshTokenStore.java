package com.runiverse.running_service.integration_test.fake;

import com.runiverse.running_service.application.auth.port.out.DeleteRefreshTokenPort;
import com.runiverse.running_service.application.auth.port.out.LoadRefreshTokenPort;
import com.runiverse.running_service.application.auth.port.out.SaveRefreshTokenHashPort;
import com.runiverse.running_service.domain.common.vo.UserId;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class InMemoryRefreshTokenStore implements SaveRefreshTokenHashPort, LoadRefreshTokenPort,
        DeleteRefreshTokenPort {

    private final Map<UUID, String> hashes = new HashMap<>();

    @Override
    public void save(UserId userId, String hashedRefreshToken) {
        hashes.put(userId.value(), hashedRefreshToken);
    }

    @Override
    public Optional<String> load(UserId userId) {
        return Optional.ofNullable(hashes.get(userId.value()));
    }

    @Override
    public void delete(UserId userId) {
        hashes.remove(userId.value());
    }

    // 검증 전용
    public Optional<String> loadById(UUID userId) {
        return Optional.ofNullable(hashes.get(userId));
    }

    public boolean isEmpty() {
        return hashes.isEmpty();
    }

    public int size() {
        return hashes.size();
    }
}
