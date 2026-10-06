package com.runiverse.running_service.integration_test.fake;

import com.runiverse.running_service.application.auth.port.out.GenerateTokenPort;
import com.runiverse.running_service.application.auth.port.out.ParseRefreshTokenPort;
import com.runiverse.running_service.application.auth.port.out.RefreshTokenHashPort;
import com.runiverse.running_service.domain.common.vo.UserId;

import java.util.Optional;
import java.util.UUID;

public class FakeTokenProvider implements GenerateTokenPort, RefreshTokenHashPort, ParseRefreshTokenPort {

    private static final String ACCESS_PREFIX = "access.";
    private static final String REFRESH_PREFIX = "refresh.";
    // 호출할 때마다 다른 토큰이 나와야 회전(rotation)을 검증할 수 있다
    private int sequence = 0;

    @Override
    public String generateAccessToken(UserId userId) {
        return ACCESS_PREFIX + userId.value() + "." + (++sequence);
    }

    @Override
    public String generateRefreshToken(UserId userId) {
        return REFRESH_PREFIX + userId.value() + "." + (++sequence);
    }

    @Override
    public Optional<UserId> parse(String refreshToken) {
        if (refreshToken == null || !refreshToken.startsWith(REFRESH_PREFIX)) {
            return Optional.empty();
        }
        String[] parts = refreshToken.split("\\.");
        if (parts.length != 3) {
            return Optional.empty();
        }
        try {
            return Optional.of(new UserId(UUID.fromString(parts[1])));
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    @Override
    public String hash(String refreshToken) {
        return "sha256:" + refreshToken;
    }

    @Override
    public boolean matches(String refreshToken, String storedHash) {
        return hash(refreshToken).equals(storedHash);
    }
}
