package com.runiverse.running_service.unit_test.infrastructure.security;

import com.runiverse.running_service.infrastructure.security.emailverification.SecureRandomVerificationTicketAdapter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

public class SecureRandomVerificationTicketAdapterTest {

    private static final int TRIALS = 1_000;
    // 32바이트를 패딩 없는 base64url로 인코딩하면 항상 43자다
    private static final int EXPECTED_LENGTH = 43;

    private final SecureRandomVerificationTicketAdapter adapter = new SecureRandomVerificationTicketAdapter();

    @Test
    @DisplayName("32바이트 엔트로피를 패딩 없는 base64url 43자로 만든다")
    void generateReturnsUrlSafeToken() {
        // when
        String ticket = adapter.generate();

        // then - 티켓은 요청 본문에 실려 오므로 URL/JSON에서 깨지는 문자가 없어야 한다
        assertThat(ticket)
                .hasSize(EXPECTED_LENGTH)
                .matches("[A-Za-z0-9_-]+")
                .doesNotContain("=", "+", "/");
    }

    @Test
    @DisplayName("발급할 때마다 다른 티켓이 나온다")
    void generateIsUnique() {
        // given
        Set<String> tickets = new HashSet<>();

        // when
        for (int i = 0; i < TRIALS; i++) {
            tickets.add(adapter.generate());
        }

        // then - 겹치면 남의 인증 이메일로 가입할 수 있다
        assertThat(tickets).hasSize(TRIALS);
    }
}
