package com.runiverse.running_service.infrastructure.security.hash;

import com.runiverse.running_service.application.auth.port.out.RefreshTokenHashPort;
import com.runiverse.running_service.application.auth.port.out.VerificationCodeHashPort;
import com.runiverse.running_service.application.auth.port.out.VerificationTicketHashPort;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Component
public class Sha256HashAdapter implements RefreshTokenHashPort, VerificationCodeHashPort, VerificationTicketHashPort {

    private static final String ALGORITHM = "SHA-256";

    @Override
    public String hash(String refreshToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance(ALGORITHM);
            return HexFormat.of()
                    .formatHex(digest.digest(refreshToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 알고리즘을 사용할 수 없습니다", e);
        }
    }

    @Override
    public boolean matches(String refreshToken, String storedHash) {
        if (storedHash == null) {
            return false;
        }
        return MessageDigest.isEqual(
                hash(refreshToken).getBytes(StandardCharsets.UTF_8),
                storedHash.getBytes(StandardCharsets.UTF_8)
        );
    }
}
