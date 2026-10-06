package com.runiverse.running_service.unit_test.infrastructure.redis;

import com.runiverse.running_service.infrastructure.redis.emailverification.EmailVerificationProperties;
import com.runiverse.running_service.infrastructure.redis.emailverification.EmailVerificationTicketRedisAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class EmailVerificationTicketRedisAdapterTest {

    private static final String EMAIL = "runner@runiverse.com";
    private static final Duration TICKET_TTL = Duration.ofMinutes(30);

    // 티켓 해시 계산은 Sha256HashAdapter의 책임이라 여기서는 해시 형태의 값만 쓴다
    private static final String HASHED_TICKET =
            "0a9b110d5e553bd98e9965c70a601c15c36805016ba60d54f20f5830c39edcde";
    private static final String EXPECTED_KEY = "user:email_verification:ticket:" + HASHED_TICKET;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private EmailVerificationTicketRedisAdapter adapter;

    @BeforeEach
    void setUp() {
        EmailVerificationProperties properties = new EmailVerificationProperties(
                Duration.ofMinutes(5), Duration.ofSeconds(60), TICKET_TTL, 5, 10);
        adapter = new EmailVerificationTicketRedisAdapter(redisTemplate, properties);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    @DisplayName("user:email_verification:ticket:{hash} 키에 이메일을 티켓 TTL과 함께 저장한다")
    void saveUsesExpectedKeyAndTtl() {
        // when
        adapter.save(HASHED_TICKET, EMAIL);

        // then - 티켓이 키다. 값 쪽에 이메일이 있어야 가입 때 꺼내 쓸 수 있다
        verify(valueOperations).set(EXPECTED_KEY, EMAIL, TICKET_TTL);
    }

    @Test
    @DisplayName("consume은 GETDEL로 조회와 동시에 지워 티켓을 1회용으로 만든다")
    void consumeReturnsEmailAndDeletes() {
        // given
        when(valueOperations.getAndDelete(EXPECTED_KEY)).thenReturn(EMAIL);

        // when & then
        assertThat(adapter.consume(HASHED_TICKET)).isEqualTo(EMAIL);
    }

    @Test
    @DisplayName("만료됐거나 이미 쓴 티켓이면 null을 반환한다")
    void consumeReturnsNullWhenAbsent() {
        // given
        when(valueOperations.getAndDelete(EXPECTED_KEY)).thenReturn(null);

        // when & then - SignUpHandler는 이 null을 EmailNotVerifiedException으로 바꾼다
        assertThat(adapter.consume(HASHED_TICKET)).isNull();
    }
}
