package com.runiverse.running_service.infrastructure.security.jwt;

import com.runiverse.running_service.application.auth.port.out.GenerateTokenPort;
import com.runiverse.running_service.application.auth.port.out.ParseRefreshTokenPort;
import com.runiverse.running_service.domain.common.vo.UserId;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class JwtTokenAdapter implements GenerateTokenPort, ParseRefreshTokenPort {

    private static final String CLAIM_TOKEN_TYPE = "typ";
    private static final String TYPE_ACCESS = "access";
    private static final String TYPE_REFRESH = "refresh";

    private final JwtEncoder accessTokenEncoder;
    private final JwtEncoder refreshTokenEncoder;
    private final JwtDecoder refreshTokenDecoder;
    private final JwtProperties properties;

    public JwtTokenAdapter(
            @Qualifier("accessTokenEncoder") JwtEncoder accessTokenEncoder,
            @Qualifier("refreshTokenEncoder") JwtEncoder refreshTokenEncoder,
            @Qualifier("refreshTokenDecoder") JwtDecoder refreshTokenDecoder,
            JwtProperties properties
    ) {
        this.accessTokenEncoder = accessTokenEncoder;
        this.refreshTokenEncoder = refreshTokenEncoder;
        this.refreshTokenDecoder = refreshTokenDecoder;
        this.properties = properties;
    }

    @Override
    public String generateAccessToken(UserId userId) {
        return generate(accessTokenEncoder, userId, TYPE_ACCESS, properties.accessToken().ttl());
    }

    @Override
    public String generateRefreshToken(UserId userId) {
        return generate(refreshTokenEncoder, userId, TYPE_REFRESH, properties.refreshToken().ttl());
    }

    private String generate(JwtEncoder encoder, UserId userId, String tokenType, Duration ttl) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.issuer())
                .audience(List.of(properties.audience()))
                .subject(userId.value().toString())
                .issuedAt(now)
                .expiresAt(now.plus(ttl))
                .id(UUID.randomUUID().toString())
                .claim(CLAIM_TOKEN_TYPE, tokenType)
                .build();
        return encoder.encode(
                JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)
        ).getTokenValue();
    }

    @Override
    public Optional<UserId> parse(String refreshToken) {
        try {
            // refresh token decode
            Jwt jwt = refreshTokenDecoder.decode(refreshToken);
            // 시크릿이 다른 경우 의도 명시
            if (!TYPE_REFRESH.equals(jwt.getClaimAsString(CLAIM_TOKEN_TYPE))) {
                return Optional.empty();
            }
            return Optional.of(new UserId(UUID.fromString(jwt.getSubject())));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
