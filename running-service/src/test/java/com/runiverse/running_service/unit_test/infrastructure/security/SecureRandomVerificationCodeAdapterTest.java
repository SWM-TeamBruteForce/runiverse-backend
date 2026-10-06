package com.runiverse.running_service.unit_test.infrastructure.security;

import com.runiverse.running_service.infrastructure.security.emailverification.SecureRandomVerificationCodeAdapter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

public class SecureRandomVerificationCodeAdapterTest {

    // 난수라 한 번만 뽑아서는 형식을 보장할 수 없다
    private static final int TRIALS = 1_000;

    private final SecureRandomVerificationCodeAdapter adapter = new SecureRandomVerificationCodeAdapter();

    @Test
    @DisplayName("항상 6자리 숫자 문자열을 만든다")
    void generateReturnsSixDigits() {
        for (int i = 0; i < TRIALS; i++) {
            // 0으로 시작해도 자릿수가 줄면 안 된다. 앞자리를 0으로 채워야 한다
            assertThat(adapter.generate()).hasSize(6).containsOnlyDigits();
        }
    }

    @Test
    @DisplayName("매번 같은 코드를 내놓지 않는다")
    void generateIsNotConstant() {
        // given
        Set<String> codes = new HashSet<>();

        // when
        for (int i = 0; i < TRIALS; i++) {
            codes.add(adapter.generate());
        }

        // then - 값이 고정이면 인증이 의미가 없다. 100만 개 중 1000개면 중복은 거의 없다
        assertThat(codes).hasSizeGreaterThan(TRIALS / 2);
    }
}
