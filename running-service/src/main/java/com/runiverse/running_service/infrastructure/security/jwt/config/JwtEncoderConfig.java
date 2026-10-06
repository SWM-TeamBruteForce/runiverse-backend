package com.runiverse.running_service.infrastructure.security.jwt.config;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.runiverse.running_service.infrastructure.security.jwt.JwtProperties;
import com.runiverse.running_service.infrastructure.security.jwt.key.JwtSecretKeyFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

@Configuration
public class JwtEncoderConfig {

    @Bean
    public JwtEncoder accessTokenEncoder(JwtProperties properties) {
        return encoder(properties.accessToken().secret());
    }

    @Bean
    public JwtEncoder refreshTokenEncoder(JwtProperties properties) {
        return encoder(properties.refreshToken().secret());
    }

    private JwtEncoder encoder(String secret) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(JwtSecretKeyFactory.create(secret)));
    }
}
