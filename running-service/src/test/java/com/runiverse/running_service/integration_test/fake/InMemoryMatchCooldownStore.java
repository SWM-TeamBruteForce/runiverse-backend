package com.runiverse.running_service.integration_test.fake;

import com.runiverse.running_service.application.match.port.out.MatchCooldownPort;
import com.runiverse.running_service.domain.common.vo.UserId;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

// MatchCooldownRedisAdapter를 대신한다 — TTL 대신 해제 시각을 들고, 지났으면 없는 것으로 본다
public class InMemoryMatchCooldownStore implements MatchCooldownPort {

    private final Map<UUID, LocalDateTime> untils = new HashMap<>();

    @Override
    public void start(UserId userId, Duration cooldown) {
        untils.put(userId.value(), LocalDateTime.now().plus(cooldown));
    }

    @Override
    public Optional<LocalDateTime> until(UserId userId) {
        return Optional.ofNullable(untils.get(userId.value()))
                .filter(until -> until.isAfter(LocalDateTime.now()));
    }
}
