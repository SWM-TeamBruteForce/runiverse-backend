package com.runiverse.running_service.unit_test.infrastructure.redis;

import com.runiverse.running_service.application.auth.port.out.VerificationAttempt;
import com.runiverse.running_service.infrastructure.redis.emailverification.EmailVerificationProperties;
import com.runiverse.running_service.infrastructure.redis.emailverification.EmailVerificationRedisAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class EmailVerificationRedisAdapterTest {

    private static final String EMAIL = "runner@runiverse.com";
    private static final String CODE_KEY = "user:email_verification:" + EMAIL;
    private static final String COOLDOWN_KEY = "user:email_verification:cooldown:" + EMAIL;
    private static final String DAILY_KEY = "user:email_verification:daily:" + EMAIL;

    private static final Duration CODE_TTL = Duration.ofMinutes(5);
    private static final Duration COOLDOWN = Duration.ofSeconds(60);
    private static final Duration DAILY_TTL = Duration.ofDays(1);
    private static final int MAX_ATTEMPTS = 5;
    private static final int DAILY_LIMIT = 10;

    // 해시 계산은 다른 어댑터의 책임이라 여기서는 해시 모양의 값만 쓴다
    private static final String HASHED_CODE =
            "0a9b110d5e553bd98e9965c70a601c15c36805016ba60d54f20f5830c39edcde";

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private HashOperations<String, String, String> hashOperations;

    private EmailVerificationRedisAdapter adapter;

    @BeforeEach
    void setUp() {
        EmailVerificationProperties properties = new EmailVerificationProperties(
                CODE_TTL, COOLDOWN, Duration.ofMinutes(30), MAX_ATTEMPTS, DAILY_LIMIT);
        adapter = new EmailVerificationRedisAdapter(redisTemplate, properties);
    }

    // Lua 스크립트가 돌려주는 응답을 흉내낸다.
    // 키나 인자가 어긋나면 스텁이 걸리지 않아 테스트가 깨진다
    private void givenScriptResult(List<String> result) {
        when(redisTemplate.<List<String>>execute(
                any(),
                eq(List.of(CODE_KEY)),
                eq("attempts"), eq(String.valueOf(MAX_ATTEMPTS)), eq("code")))
                .thenReturn(result);
    }

    @Test
    @DisplayName("쿨다운은 SET NX로 선점하고 성공하면 true를 반환한다")
    void tryAcquireReturnsTrueWhenAbsent() {
        // given
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(COOLDOWN_KEY, "1", COOLDOWN)).thenReturn(true);

        // when & then
        assertThat(adapter.tryAcquire(EMAIL)).isTrue();
    }

    @Test
    @DisplayName("이미 쿨다운이 잡혀 있으면 false를 반환한다")
    void tryAcquireReturnsFalseWhenPresent() {
        // given
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(COOLDOWN_KEY, "1", COOLDOWN)).thenReturn(false);

        // when & then
        assertThat(adapter.tryAcquire(EMAIL)).isFalse();
    }

    @Test
    @DisplayName("setIfAbsent가 null을 반환해도 선점 실패로 본다")
    void tryAcquireReturnsFalseWhenNull() {
        // given - 파이프라인이나 트랜잭션 중이면 null이 온다. 열어 주면 중복 발송이 뚫린다
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(COOLDOWN_KEY, "1", COOLDOWN)).thenReturn(null);

        // when & then
        assertThat(adapter.tryAcquire(EMAIL)).isFalse();
    }

    @Test
    @DisplayName("release는 쿨다운 키만 지운다")
    void releaseDeletesCooldownKey() {
        // when
        adapter.release(EMAIL);

        // then
        verify(redisTemplate).delete(COOLDOWN_KEY);
    }

    @Test
    @DisplayName("첫 발송이면 일일 카운터에 1일 만료를 건다")
    void tryConsumeSetsTtlOnFirstCall() {
        // given
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(DAILY_KEY)).thenReturn(1L);

        // when & then
        assertThat(adapter.tryConsume(EMAIL)).isTrue();
        verify(redisTemplate).expire(DAILY_KEY, DAILY_TTL);
    }

    @Test
    @DisplayName("두 번째 발송부터는 만료를 다시 걸지 않는다")
    void tryConsumeDoesNotRenewTtl() {
        // given - 매번 갱신하면 하루 창이 영원히 닫히지 않는다
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(DAILY_KEY)).thenReturn(2L);

        // when & then
        assertThat(adapter.tryConsume(EMAIL)).isTrue();
        verify(redisTemplate, never()).expire(eq(DAILY_KEY), any(Duration.class));
    }

    @Test
    @DisplayName("일일 한도와 같은 횟수까지는 허용한다")
    void tryConsumeAllowsUpToLimit() {
        // given
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(DAILY_KEY)).thenReturn((long) DAILY_LIMIT);

        // when & then
        assertThat(adapter.tryConsume(EMAIL)).isTrue();
    }

    @Test
    @DisplayName("일일 한도를 넘으면 false를 반환한다")
    void tryConsumeRejectsOverLimit() {
        // given
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(DAILY_KEY)).thenReturn(DAILY_LIMIT + 1L);

        // when & then
        assertThat(adapter.tryConsume(EMAIL)).isFalse();
    }

    @Test
    @DisplayName("increment가 null을 반환하면 발송을 막는다")
    void tryConsumeRejectsWhenIncrementIsNull() {
        // given
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(DAILY_KEY)).thenReturn(null);

        // when & then
        assertThat(adapter.tryConsume(EMAIL)).isFalse();
    }

    @Test
    @DisplayName("코드는 해시와 시도 횟수 0을 함께 저장하고 코드 TTL을 건다")
    void saveWritesCodeAndResetsAttempts() {
        // given
        when(redisTemplate.<String, String>opsForHash()).thenReturn(hashOperations);

        // when
        adapter.save(EMAIL, HASHED_CODE);

        // then - 재발송이면 시도 횟수도 함께 0으로 덮어써야 한다
        verify(hashOperations).putAll(CODE_KEY, Map.of("code", HASHED_CODE, "attempts", "0"));
        verify(redisTemplate).expire(CODE_KEY, CODE_TTL);
    }

    @Test
    @DisplayName("delete는 코드 키만 지운다")
    void deleteRemovesCodeKey() {
        // when
        adapter.delete(EMAIL);

        // then - 쿨다운과 일일 카운터는 남아 있어야 한다
        verify(redisTemplate).delete(CODE_KEY);
    }

    @Test
    @DisplayName("스크립트가 AVAILABLE과 해시를 주면 그대로 옮겨 담는다")
    void consumeReturnsAvailable() {
        // given
        givenScriptResult(List.of("AVAILABLE", HASHED_CODE));

        // when
        VerificationAttempt attempt = adapter.consume(EMAIL);

        // then
        assertThat(attempt.status()).isEqualTo(VerificationAttempt.Status.AVAILABLE);
        assertThat(attempt.hashedCode()).isEqualTo(HASHED_CODE);
    }

    @Test
    @DisplayName("스크립트가 NOT_FOUND를 주면 해시 없이 NOT_FOUND를 반환한다")
    void consumeReturnsNotFound() {
        // given
        givenScriptResult(List.of("NOT_FOUND"));

        // when
        VerificationAttempt attempt = adapter.consume(EMAIL);

        // then
        assertThat(attempt.status()).isEqualTo(VerificationAttempt.Status.NOT_FOUND);
        assertThat(attempt.hashedCode()).isNull();
    }

    @Test
    @DisplayName("스크립트가 EXHAUSTED를 주면 해시 없이 EXHAUSTED를 반환한다")
    void consumeReturnsExhausted() {
        // given
        givenScriptResult(List.of("EXHAUSTED"));

        // when
        VerificationAttempt attempt = adapter.consume(EMAIL);

        // then
        assertThat(attempt.status()).isEqualTo(VerificationAttempt.Status.EXHAUSTED);
        assertThat(attempt.hashedCode()).isNull();
    }

    @Test
    @DisplayName("스크립트 응답이 null이면 코드가 없는 것으로 본다")
    void consumeTreatsNullAsNotFound() {
        // given
        givenScriptResult(null);

        // when & then - 열어 주면 해시 없이 인증이 통과할 수 있다
        assertThat(adapter.consume(EMAIL).status()).isEqualTo(VerificationAttempt.Status.NOT_FOUND);
    }

    @Test
    @DisplayName("스크립트 응답이 비어 있어도 코드가 없는 것으로 본다")
    void consumeTreatsEmptyAsNotFound() {
        // given
        givenScriptResult(List.of());

        // when & then
        assertThat(adapter.consume(EMAIL).status()).isEqualTo(VerificationAttempt.Status.NOT_FOUND);
    }

    @Test
    @DisplayName("AVAILABLE인데 해시가 빠져 있으면 손상된 데이터로 보고 NOT_FOUND를 반환한다")
    void consumeTreatsAvailableWithoutHashAsNotFound() {
        // given - HGET이 nil을 돌려주면 리스트 길이가 1이 된다
        givenScriptResult(List.of("AVAILABLE"));

        // when
        VerificationAttempt attempt = adapter.consume(EMAIL);

        // then
        assertThat(attempt.status()).isEqualTo(VerificationAttempt.Status.NOT_FOUND);
        assertThat(attempt.hashedCode()).isNull();
    }
}
