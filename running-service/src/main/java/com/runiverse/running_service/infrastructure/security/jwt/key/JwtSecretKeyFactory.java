package com.runiverse.running_service.infrastructure.security.jwt.key;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

public record JwtSecretKeyFactory() {

    private static final String ALGORITHM = "HmacSHA256"; // HS256에 맞춘 encoder
    private static final int MIN_SECRET_BYTES = 32; // 암호키는 최소 32바이트로 하였다.

    public static SecretKeySpec create(String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("JWT 시크릿이 설정되지 않았습니다");
        }

        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "JWT 시크릿은 최소 %d바이트여야 합니다 (현재 %d바이트)"
                            .formatted(MIN_SECRET_BYTES, keyBytes.length)
            );
        }

        return new SecretKeySpec(keyBytes, ALGORITHM);
    }
}
