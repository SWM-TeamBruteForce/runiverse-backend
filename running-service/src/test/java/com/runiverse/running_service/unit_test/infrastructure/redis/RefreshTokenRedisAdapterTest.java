package com.runiverse.running_service.unit_test.infrastructure.redis;

import com.github.f4b6a3.uuid.UuidCreator;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.infrastructure.redis.token.RefreshTokenRedisAdapter;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class RefreshTokenRedisAdapterTest {

    // 지문 계산은 Sha256RefreshTokenHashAdapter의 책임이라 여기서는 해시 형태의 값만 쓴다
    private static final String HASHED_REFRESH_TOKEN =
            "0a9b110d5e553bd98e9965c70a601c15c36805016ba60d54f20f5830c39edcde";

    private static final Duration REFRESH_TTL = Duration.ofDays(14);

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private RefreshTokenRedisAdapter refreshTokenRedisAdapter;
    private UserId userId;
    private String expectedKey;

    @BeforeEach
    void setUp() {
        JwtProperties jwtProperties = new JwtProperties(
                "runiverse",
                "runiverse-api",
                new JwtProperties.TokenSpec("access-secret-for-test-must-be-at-least-32-bytes", Duration.ofMinutes(30)),
                new JwtProperties.TokenSpec("refresh-secret-for-test-must-be-at-least-32-bytes", REFRESH_TTL)
        );

        refreshTokenRedisAdapter = new RefreshTokenRedisAdapter(redisTemplate, jwtProperties);
        userId = new UserId(UuidCreator.getTimeOrderedEpoch());
        expectedKey = "user:" + userId.value() + ":refresh_token";
    }

    @Test
    @DisplayName("user:{userId}:refresh_token 키에 refresh token TTL과 함께 저장한다")
    void saveUsesExpectedKeyAndTtl() {
        // given
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        // when
        refreshTokenRedisAdapter.save(userId, HASHED_REFRESH_TOKEN);

        // then
        verify(valueOperations).set(expectedKey, HASHED_REFRESH_TOKEN, REFRESH_TTL);
    }

    @Test
    @DisplayName("저장된 해시를 그대로 반환한다")
    void loadReturnsStoredHash() {
        // given
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(expectedKey)).thenReturn(HASHED_REFRESH_TOKEN);

        // when
        Optional<String> loaded = refreshTokenRedisAdapter.load(userId);

        // then
        assertThat(loaded).contains(HASHED_REFRESH_TOKEN);
    }

    @Test
    @DisplayName("저장된 값이 없으면 빈 Optional을 반환한다")
    void loadReturnsEmptyWhenAbsent() {
        // given
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(expectedKey)).thenReturn(null);

        // when & then
        assertThat(refreshTokenRedisAdapter.load(userId)).isEmpty();
    }

    @Test
    @DisplayName("user:{userId}:refresh_token 키를 삭제한다")
    void deleteRemovesKey() {
        // when
        refreshTokenRedisAdapter.delete(userId);

        // then
        verify(redisTemplate).delete(expectedKey);
    }
}
