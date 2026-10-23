package com.runiverse.running_service.infrastructure.oauth.apple;

import com.runiverse.running_service.application.auth.exception.OauthEmailNotProvidedException;
import com.runiverse.running_service.application.auth.exception.OauthLoginFailedException;
import com.runiverse.running_service.application.auth.exception.OauthProviderUnavailableException;
import com.runiverse.running_service.application.auth.port.out.LoadAppleProfilePort;
import com.runiverse.running_service.application.auth.port.out.OauthProfile;
import com.runiverse.running_service.domain.user.vo.Provider;
import com.runiverse.running_service.infrastructure.security.jwt.validator.AudienceValidator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.restclient.RestTemplateBuilder;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

// 앱이 애플 로그인으로 받아 온 ID 토큰을 애플 공개키로 검증한다 — 애플과의 통신은 공개키 조회뿐이다
@Slf4j
@Component
public class AppleOauthClient implements LoadAppleProfilePort {

    private static final String ISSUER = "https://appleid.apple.com";
    private final JwtDecoder decoder;

    AppleOauthClient(AppleOauthProperties properties, RestTemplateBuilder restTemplateBuilder) {
        // 기본 RestTemplate은 타임아웃이 없다 — spring.http.client 타임아웃이 걸린 빌더로 만든다
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(properties.jwkSetUri())
                .restOperations(restTemplateBuilder.build())
                .build();
        // 기본 검증기는 exp가 없으면 만료 검사를 건너뛴다 — 없는 토큰은 거절한다
        JwtTimestampValidator timestampValidator = new JwtTimestampValidator();
        timestampValidator.setAllowEmptyExpiryClaim(false);
        decoder.setJwtValidator(JwtValidators.createDefaultWithValidators(
                timestampValidator,
                new JwtClaimValidator<Object>(JwtClaimNames.ISS, iss -> iss != null && ISSUER.equals(iss.toString())),
                new AudienceValidator(properties.clientId()),
                new JwtClaimValidator<String>(JwtClaimNames.SUB, StringUtils::hasText)
        ));
        this.decoder = decoder;
    }

    @Override
    public OauthProfile load(String idToken, String nonce) {
        Jwt jwt = decode(idToken);
        // nonce는 요청마다 달라 디코더 검증기에 못 넣는다 — 해석이 끝난 뒤 직접 대조한다
        if (!nonceMatches(jwt.getClaimAsString("nonce"), nonce)) {
            log.warn("[인증] 애플 로그인 실패: nonce 불일치");
            throw new OauthLoginFailedException();
        }
        // email 스코프에 동의하지 않았거나 애플이 소유를 확인하지 않은 이메일은 가입에 쓰지 않는다
        String email = jwt.getClaimAsString("email");
        if (!StringUtils.hasText(email) || !Boolean.TRUE.equals(jwt.getClaimAsBoolean("email_verified"))) {
            throw new OauthEmailNotProvidedException();
        }
        return new OauthProfile(Provider.APPLE, jwt.getSubject(), email);
    }

    // 앱이 애플에 넘긴 값 = SHA-256(UTF-8(원본))의 소문자 hex — 토큰의 nonce 클레임과 상수 시간으로 비교한다
    private static boolean nonceMatches(String claim, String rawNonce) {
        if (claim == null) {
            return false;
        }
        byte[] expected = HexFormat.of().formatHex(sha256(rawNonce)).getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(claim.getBytes(StandardCharsets.UTF_8), expected);
    }

    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 알고리즘을 사용할 수 없습니다", e);
        }
    }

    private Jwt decode(String idToken) {
        try {
            return decoder.decode(idToken);
        } catch (JwtValidationException e) {
            // 만료·iss·aud가 맞지 않는다 — aud 불일치는 앱의 번들 ID와 APPLE_CLIENT_ID를 먼저 본다
            List<String> reasons = e.getErrors().stream()
                    .map(OAuth2Error::getDescription)
                    .toList();
            log.warn("[인증] 애플 로그인 실패: ID 토큰 규칙 위반 - reasons={}", reasons);
            throw new OauthLoginFailedException();
        } catch (BadJwtException e) {
            // 형식이 깨졌거나 서명·키가 맞지 않는다 — 라이브러리 메시지에 무엇이 담길지 알 수 없어 직접 원인의 종류만 남긴다
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            log.warn("[인증] 애플 로그인 실패: ID 토큰 해석·서명 검증 실패 - cause={}",
                    cause.getClass().getSimpleName());
            throw new OauthLoginFailedException();
        } catch (JwtException e) {
            // 토큰이 아니라 공개키를 받아 오지 못한 것이다 — 애플 장애인지 우리 네트워크 문제인지는 여기서 가를 수 없다
            log.error("[인증] 애플 로그인 실패: 애플 공개키 조회 오류", e);
            throw new OauthProviderUnavailableException();
        }
    }
}
