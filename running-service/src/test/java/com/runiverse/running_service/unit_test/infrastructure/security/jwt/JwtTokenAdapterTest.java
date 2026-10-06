package com.runiverse.running_service.unit_test.infrastructure.security.jwt;

import com.github.f4b6a3.uuid.UuidCreator;
import com.runiverse.running_service.application.auth.port.out.CheckBlockedAccessTokenPort;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.infrastructure.security.jwt.JwtProperties;
import com.runiverse.running_service.infrastructure.security.jwt.JwtTokenAdapter;
import com.runiverse.running_service.infrastructure.security.jwt.config.JwtDecoderConfig;
import com.runiverse.running_service.infrastructure.security.jwt.config.JwtEncoderConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

public class JwtTokenAdapterTest {

    private static final String ISSUER = "runiverse";
    private static final String AUDIENCE = "runiverse-api";
    private static final String ACCESS_SECRET = "access-secret-for-test-must-be-at-least-32-bytes";
    private static final String REFRESH_SECRET = "refresh-secret-for-test-must-be-at-least-32-bytes";
    private static final Duration ACCESS_TTL = Duration.ofMinutes(30);
    private static final Duration REFRESH_TTL = Duration.ofDays(14);
    private static final CheckBlockedAccessTokenPort NEVER_BLOCKED = accessTokenId -> false;
    private JwtEncoderConfig jwtEncoderConfig;
    private JwtDecoderConfig jwtDecoderConfig;
    private JwtProperties jwtProperties;
    private JwtTokenAdapter jwtTokenAdapter;
    private JwtDecoder accessTokenDecoder;
    private UserId userId;

    @BeforeEach
    void setUp() {
        jwtProperties = new JwtProperties(
                ISSUER,
                AUDIENCE,
                new JwtProperties.TokenSpec(ACCESS_SECRET, ACCESS_TTL),
                new JwtProperties.TokenSpec(REFRESH_SECRET, REFRESH_TTL)
        );

        // 목이 아닌 실제 빈을 조립해 발급-검증 경로가 맞물리는지 확인한다
        jwtEncoderConfig = new JwtEncoderConfig();
        jwtDecoderConfig = new JwtDecoderConfig();
        accessTokenDecoder = jwtDecoderConfig.accessTokenDecoder(jwtProperties, NEVER_BLOCKED);

        jwtTokenAdapter = new JwtTokenAdapter(
                jwtEncoderConfig.accessTokenEncoder(jwtProperties),
                jwtEncoderConfig.refreshTokenEncoder(jwtProperties),
                jwtDecoderConfig.refreshTokenDecoder(jwtProperties),
                jwtProperties
        );

        userId = new UserId(UuidCreator.getTimeOrderedEpoch());
    }

    @Test
    @DisplayName("access token에 issuer, audience, subject, typ 클레임을 담는다")
    void generateAccessTokenContainsClaims() {
        // when
        Jwt jwt = accessTokenDecoder.decode(jwtTokenAdapter.generateAccessToken(userId));

        // then
        assertThat(jwt.getClaimAsString("iss")).isEqualTo(ISSUER);
        assertThat(jwt.getAudience()).containsExactly(AUDIENCE);
        assertThat(jwt.getSubject()).isEqualTo(userId.value().toString());
        assertThat(jwt.getClaimAsString("typ")).isEqualTo("access");
        assertThat(jwt.getId()).isNotBlank();
    }

    @Test
    @DisplayName("access token 만료는 발급 시각 + access TTL이다")
    void generateAccessTokenAppliesAccessTtl() {
        // when
        Jwt jwt = accessTokenDecoder.decode(jwtTokenAdapter.generateAccessToken(userId));

        // then
        assertThat(jwt.getExpiresAt()).isCloseTo(
                jwt.getIssuedAt().plus(ACCESS_TTL),
                within(1, ChronoUnit.SECONDS)
        );
    }

    @Test
    @DisplayName("호출할 때마다 jti가 다른 토큰을 발급한다")
    void generateAccessTokenIssuesUniqueJti() {
        // when
        Jwt first = accessTokenDecoder.decode(jwtTokenAdapter.generateAccessToken(userId));
        Jwt second = accessTokenDecoder.decode(jwtTokenAdapter.generateAccessToken(userId));

        // then
        assertThat(first.getId()).isNotEqualTo(second.getId());
    }

    @Test
    @DisplayName("refresh token은 세 부분으로 이루어진 JWT를 반환한다")
    void generateRefreshTokenReturnsJwt() {
        // when
        String refreshToken = jwtTokenAdapter.generateRefreshToken(userId);

        // then
        assertThat(refreshToken.split("\\.")).hasSize(3);
    }

    @Test
    @DisplayName("refresh token은 키가 달라 access 디코더로 검증되지 않는다")
    void refreshTokenIsRejectedByAccessDecoder() {
        // given
        String refreshToken = jwtTokenAdapter.generateRefreshToken(userId);

        // when & then
        assertThatThrownBy(() -> accessTokenDecoder.decode(refreshToken))
                .isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("발급한 refresh token을 파싱하면 원래 userId가 나온다")
    void parseReturnUserIdFromRefreshToken() {
        // given
        String refreshToken = jwtTokenAdapter.generateRefreshToken(userId);

        // when & then
        assertThat(jwtTokenAdapter.parse(refreshToken)).contains(userId);
    }

    @Test
    @DisplayName("access token은 키가 달라 refresh token으로 파싱되지 않는다")
    void parseRejectAccessToken() {
        // given
        String accessToken = jwtTokenAdapter.generateAccessToken(userId);
        // when & then
        assertThat(jwtTokenAdapter.parse(accessToken)).isEmpty();
    }

    @Test
    @DisplayName("JWT 형식이 아니면 빈 값을 반환한다")
    void parseRejectMalFormedToken() {
        // when & then
        assertThat(jwtTokenAdapter.parse("not-a-jwt")).isEmpty();
    }

    @Test
    @DisplayName("만료된 refresh token은 빈 값을 반환한다")
    void parseRejectsExpiredToken() {
        // given
        String expiredToken = signRefreshToken(
                Instant.now().minus(Duration.ofHours(2)),   // 2시간 전 발급
                Instant.now().minus(Duration.ofHours(1))    // 1시간 전 만료
        );

        // when & then
        assertThat(jwtTokenAdapter.parse(expiredToken)).isEmpty();
    }

    /**
     * 만료 시각을 직접 지정한 refresh token을 발급한다.
     * JwtTokenAdapter는 TTL을 현재 시각 기준으로만 계산해 과거 만료를 만들 수 없으므로,
     * 같은 키를 쓰는 인코더로 클레임을 직접 조립한다.
     */
    private String signRefreshToken(Instant issuedAt, Instant expiresAt) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .audience(List.of(AUDIENCE))
                .subject(userId.value().toString())
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .id(UUID.randomUUID().toString())
                .claim("typ", "refresh")
                .build();

        return jwtEncoderConfig.refreshTokenEncoder(jwtProperties)
                .encode(JwtEncoderParameters.from(
                        JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }
}
