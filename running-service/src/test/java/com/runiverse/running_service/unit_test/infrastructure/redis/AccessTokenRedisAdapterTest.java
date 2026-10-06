package com.runiverse.running_service.unit_test.infrastructure.redis;

import com.runiverse.running_service.infrastructure.redis.token.AccessTokenRedisAdapter;
import com.runiverse.running_service.infrastructure.security.jwt.JwtProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class AccessTokenRedisAdapterTest {

    private static final Duration ACCESS_TTL = Duration.ofMinutes(30);
    private static final String BLOCKED = "1";
    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;
    private AccessTokenRedisAdapter accessTokenRedisAdapter;
    private String accessTokenId;
    private String expectedKey;

    @BeforeEach
    void setUp() {
        JwtProperties jwtProperties = new JwtProperties(
                "runiverse",
                "runiverse-api",
                new JwtProperties.TokenSpec("access-secret-for-test-must-be-at-least-32-bytes", ACCESS_TTL),
                new JwtProperties.TokenSpec("refresh-secret-for-test-must-be-at-least-32-bytes", Duration.ofDays(14))
        );
        accessTokenRedisAdapter = new AccessTokenRedisAdapter(redisTemplate, jwtProperties);
        accessTokenId = UUID.randomUUID().toString();
        expectedKey = "user:access_token:blacklist:" + accessTokenId;
    }

    @Test
    @DisplayName("user:access_token:blacklist:{jti} 키에 access token TTL과 함께 등록한다")
    void blockUsesExpectedKeyAndTtl() {
        // given
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        // when
        accessTokenRedisAdapter.block(accessTokenId);
        // then
        verify(valueOperations).set(expectedKey, BLOCKED, ACCESS_TTL);
    }

    @Test
    @DisplayName("등록된 jti는 차단된 것으로 판단한다")
    void isBlockedReturnsTrueWhenKeyExists() {
        // given
        when(redisTemplate.hasKey(expectedKey)).thenReturn(true);
        // when & then
        assertThat(accessTokenRedisAdapter.isBlocked(accessTokenId)).isTrue();
    }

    @Test
    @DisplayName("등록되지 않은 jti는 통과시킨다")
    void isBlockedReturnsFalseWhenKeyAbsent() {
        // given
        when(redisTemplate.hasKey(expectedKey)).thenReturn(false);
        // when & then
        assertThat(accessTokenRedisAdapter.isBlocked(accessTokenId)).isFalse();
    }

    @Test
    @DisplayName("hasKey가 null을 반환해도 통과시킨다")
    void isBlockedReturnsFalseWhenHasKeyIsNull() {
        // given
        when(redisTemplate.hasKey(expectedKey)).thenReturn(null);
        // when & then
        assertThat(accessTokenRedisAdapter.isBlocked(accessTokenId)).isFalse();
    }
}
