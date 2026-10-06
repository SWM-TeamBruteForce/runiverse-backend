package com.runiverse.running_service.infrastructure.redis.match;

import com.runiverse.running_service.application.match.port.out.MatchCooldownPort;
import com.runiverse.running_service.application.running.port.out.StartMatchCooldownPort;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.infrastructure.redis.RedisKey;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class MatchCooldownRedisAdapter implements MatchCooldownPort, StartMatchCooldownPort {

    private static final String COOLDOWN = "cooldown";
    private static final String BLOCKED = "1";
    private final StringRedisTemplate redisTemplate;

    @Override
    public void start(UserId userId, Duration cooldown) {
        redisTemplate.opsForValue().set(key(userId), BLOCKED, cooldown);
    }

    @Override
    public Optional<LocalDateTime> until(UserId userId) {
        // 남은 시간을 그대로 읽는다 — 저장 시각을 따로 담지 않아도 해제 시각이 나온다.
        // 키가 없으면 -2, TTL이 없으면 -1이 오므로 양수일 때만 쿨다운이다
        Long remainingSeconds = redisTemplate.getExpire(key(userId));
        if (remainingSeconds == null || remainingSeconds <= 0) {
            return Optional.empty();
        }
        return Optional.of(LocalDateTime.now().plusSeconds(remainingSeconds));
    }

    private String key(UserId userId) {
        return RedisKey.MATCH.of(COOLDOWN, userId.value().toString());
    }
}
