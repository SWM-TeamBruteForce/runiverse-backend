package com.runiverse.running_service.integration_test.fake;

import com.runiverse.running_service.application.auth.port.out.BlockAccessTokenPort;
import com.runiverse.running_service.application.auth.port.out.CheckBlockedAccessTokenPort;

import java.util.HashSet;
import java.util.Set;

public class InMemoryAccessTokenBlacklist implements BlockAccessTokenPort, CheckBlockedAccessTokenPort {

    private final Set<String> blocked = new HashSet<>();

    @Override
    public void block(String accessTokenId) {
        blocked.add(accessTokenId);
    }

    @Override
    public boolean isBlocked(String accessTokenId) {
        return blocked.contains(accessTokenId);
    }

    // 검증 전용
    public int size() {
        return blocked.size();
    }
}
