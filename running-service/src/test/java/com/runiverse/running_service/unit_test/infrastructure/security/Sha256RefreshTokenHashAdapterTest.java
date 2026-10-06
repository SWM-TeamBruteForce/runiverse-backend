package com.runiverse.running_service.unit_test.infrastructure.security;

import com.runiverse.running_service.infrastructure.security.hash.Sha256HashAdapter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class Sha256RefreshTokenHashAdapterTest {

    // "test-refresh-token"의 SHA-256 (sha256sum으로 확인한 값)
    private static final String REFRESH_TOKEN = "test-refresh-token";
    private static final String EXPECTED_HASH =
            "0a9b110d5e553bd98e9965c70a601c15c36805016ba60d54f20f5830c39edcde";

    private final Sha256HashAdapter adapter = new Sha256HashAdapter();

    @Test
    @DisplayName("토큰 원문이 아니라 SHA-256 지문을 만든다")
    void hashReturnsSha256Fingerprint() {
        // when
        String hashed = adapter.hash(REFRESH_TOKEN);

        // then
        assertThat(hashed)
                .isNotEqualTo(REFRESH_TOKEN)
                .hasSize(64)
                .isEqualTo(EXPECTED_HASH);
    }

    @Test
    @DisplayName("같은 토큰은 항상 같은 지문을 만든다")
    void hashIsDeterministic() {
        // when & then
        assertThat(adapter.hash(REFRESH_TOKEN)).isEqualTo(adapter.hash(REFRESH_TOKEN));
    }

    @Test
    @DisplayName("저장된 지문과 같은 토큰이면 true를 반환한다")
    void matchesReturnsTrueForSameToken() {
        // when & then
        assertThat(adapter.matches(REFRESH_TOKEN, EXPECTED_HASH)).isTrue();
    }

    @Test
    @DisplayName("다른 토큰이면 false를 반환한다")
    void matchesReturnsFalseForDifferentToken() {
        // when & then
        assertThat(adapter.matches("other-refresh-token", EXPECTED_HASH)).isFalse();
    }

    @Test
    @DisplayName("저장된 지문이 없으면 false를 반환한다")
    void matchesReturnsFalseWhenStoredHashIsNull() {
        // when & then
        assertThat(adapter.matches(REFRESH_TOKEN, null)).isFalse();
    }
}
