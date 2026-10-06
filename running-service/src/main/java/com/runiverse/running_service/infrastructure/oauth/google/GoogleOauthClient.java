package com.runiverse.running_service.infrastructure.oauth.google;

import com.runiverse.running_service.application.auth.exception.OauthEmailNotProvidedException;
import com.runiverse.running_service.application.auth.exception.OauthLoginFailedException;
import com.runiverse.running_service.application.auth.exception.OauthProviderUnavailableException;
import com.runiverse.running_service.application.auth.port.out.LoadGoogleProfilePort;
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
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Set;

// 앱이 구글 로그인으로 받아 온 ID 토큰을 구글 공개키로 검증한다 — 구글과의 통신은 공개키 조회뿐이다
@Slf4j
@Component
public class GoogleOauthClient implements LoadGoogleProfilePort {

    // 구글은 iss를 두 표기 중 하나로 발급한다
    private static final Set<String> ISSUERS = Set.of("accounts.google.com", "https://accounts.google.com");
    private final JwtDecoder decoder;

    GoogleOauthClient(GoogleOauthProperties properties, RestTemplateBuilder restTemplateBuilder) {
        // 기본 RestTemplate은 타임아웃이 없다 — spring.http.client 타임아웃이 걸린 빌더로 만든다
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(properties.jwkSetUri())
                .restOperations(restTemplateBuilder.build())
                .build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithValidators(
                new JwtClaimValidator<Object>(JwtClaimNames.ISS, iss -> iss != null && ISSUERS.contains(iss.toString())),
                new AudienceValidator(properties.clientId()),
                new JwtClaimValidator<String>(JwtClaimNames.SUB, StringUtils::hasText)
        ));
        this.decoder = decoder;
    }

    @Override
    public OauthProfile load(String idToken) {
        Jwt jwt = decode(idToken);
        // email 스코프에 동의하지 않았거나 구글이 소유를 확인하지 않은 이메일은 가입에 쓰지 않는다
        String email = jwt.getClaimAsString("email");
        if (!StringUtils.hasText(email) || !Boolean.TRUE.equals(jwt.getClaimAsBoolean("email_verified"))) {
            throw new OauthEmailNotProvidedException();
        }
        return new OauthProfile(Provider.GOOGLE, jwt.getSubject(), email);
    }

    private Jwt decode(String idToken) {
        try {
            return decoder.decode(idToken);
        } catch (JwtValidationException e) {
            // 만료·iss·aud가 맞지 않는다 — aud 불일치는 앱의 serverClientId 설정을 먼저 본다
            List<String> reasons = e.getErrors().stream()
                    .map(OAuth2Error::getDescription)
                    .toList();
            log.warn("[인증] 구글 로그인 실패: ID 토큰 규칙 위반 - reasons={}", reasons);
            throw new OauthLoginFailedException();
        } catch (BadJwtException e) {
            // 형식이 깨졌거나 서명·키가 맞지 않는다 — 라이브러리 메시지에 무엇이 담길지 알 수 없어 직접 원인의 종류만 남긴다
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            log.warn("[인증] 구글 로그인 실패: ID 토큰 해석·서명 검증 실패 - cause={}",
                    cause.getClass().getSimpleName());
            throw new OauthLoginFailedException();
        } catch (JwtException e) {
            // 토큰이 아니라 공개키를 받아 오지 못한 것이다 — 구글 장애인지 우리 네트워크 문제인지는 여기서 가를 수 없다
            log.error("[인증] 구글 로그인 실패: 구글 공개키 조회 오류", e);
            throw new OauthProviderUnavailableException();
        }
    }
}
