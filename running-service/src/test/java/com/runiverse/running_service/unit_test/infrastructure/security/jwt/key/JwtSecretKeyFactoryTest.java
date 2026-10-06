package com.runiverse.running_service.unit_test.infrastructure.security.jwt.key;

import com.runiverse.running_service.infrastructure.security.jwt.key.JwtSecretKeyFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.crypto.spec.SecretKeySpec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class JwtSecretKeyFactoryTest {

    @Test
    @DisplayName("32바이트 이상 시크릿으로 HmacSHA256 키를 만든다")
    void createReturnHmacKey() {
        // given
        String secret = "a".repeat(32);

        // when
        SecretKeySpec key = JwtSecretKeyFactory.create(secret);

        // then
        assertThat(key.getAlgorithm()).isEqualTo("HmacSHA256");
        assertThat(key.getEncoded()).hasSize(32);
    }

    @Test
    @DisplayName("32바이트 미만 시크릿은 거부한다")
    void createRejectsShortSecret() {
        // when & then
        assertThatThrownBy(() -> JwtSecretKeyFactory.create("a".repeat(31)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("31바이트");
    }

    @Test
    @DisplayName("시크릿이 비어 있으면 거부한다")
    void createRejectsBlankSecret() {
        // when & then
        assertThatThrownBy(() -> JwtSecretKeyFactory.create(null))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> JwtSecretKeyFactory.create("  "))
                .isInstanceOf(IllegalStateException.class);
    }
}
