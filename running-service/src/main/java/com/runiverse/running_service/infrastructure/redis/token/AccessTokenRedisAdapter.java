package com.runiverse.running_service.infrastructure.redis.token;

import com.runiverse.running_service.application.auth.port.out.BlockAccessTokenPort;
import com.runiverse.running_service.application.auth.port.out.CheckBlockedAccessTokenPort;
import com.runiverse.running_service.infrastructure.redis.RedisKey;
import com.runiverse.running_service.infrastructure.security.jwt.JwtProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AccessTokenRedisAdapter implements BlockAccessTokenPort, CheckBlockedAccessTokenPort {

    private final StringRedisTemplate redisTemplate;
    private final JwtProperties jwtProperties;
    private static final String ACCESS_TOKEN = "access_token";
    private static final String BLACKLIST = "blacklist";
    private static final String BLOCKED = "1";

    @Override
    public void block(String accessTokenId) {
        redisTemplate.opsForValue().set(
                key(accessTokenId),
                BLOCKED,
                jwtProperties.accessToken().ttl()
        );
    }

    @Override
    public boolean isBlocked(String accessTokenId) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(key(accessTokenId)));
    }

    private String key(String accessTokenId) {
        return RedisKey.USER.of(ACCESS_TOKEN, BLACKLIST, accessTokenId);
    }
}
