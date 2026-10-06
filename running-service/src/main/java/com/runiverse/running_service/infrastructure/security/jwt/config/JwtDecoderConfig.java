package com.runiverse.running_service.infrastructure.security.jwt.config;

import com.runiverse.running_service.application.auth.port.out.CheckBlockedAccessTokenPort;
import com.runiverse.running_service.infrastructure.security.jwt.JwtProperties;
import com.runiverse.running_service.infrastructure.security.jwt.key.JwtSecretKeyFactory;
import com.runiverse.running_service.infrastructure.security.jwt.validator.AudienceValidator;
import com.runiverse.running_service.infrastructure.security.jwt.validator.BlockedTokenValidator;
import com.runiverse.running_service.infrastructure.security.jwt.validator.ExpiredTokenValidator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;


@Configuration
public class JwtDecoderConfig {

    @Bean
    public JwtDecoder accessTokenDecoder(
            JwtProperties properties,
            CheckBlockedAccessTokenPort checkBlockedAccessTokenPort
    ) {
        NimbusJwtDecoder decoder = decoder(properties.accessToken().secret());
        decoder.setJwtValidator(JwtValidators.createDefaultWithValidators(
                new JwtIssuerValidator(properties.issuer()), // ISSUER 체크
                new AudienceValidator(properties.audience()), // AUDIENCE 체크
                new ExpiredTokenValidator(), // 만료 토큰 체크
                new BlockedTokenValidator(checkBlockedAccessTokenPort) // 블랙리스트 체크
        ));
        return decoder;
    }

    @Bean
    public JwtDecoder refreshTokenDecoder(JwtProperties properties) {
        NimbusJwtDecoder decoder = decoder(properties.refreshToken().secret());
        decoder.setJwtValidator(JwtValidators.createDefaultWithValidators(
                new JwtIssuerValidator(properties.issuer()),
                new AudienceValidator(properties.audience())
        ));
        return decoder;
    }

    // 키와 알고리즘만 조립
    private NimbusJwtDecoder decoder(String secret) {
        return NimbusJwtDecoder
                .withSecretKey(JwtSecretKeyFactory.create(secret))
                .macAlgorithm(MacAlgorithm.HS256)
                .build();

    }
}
