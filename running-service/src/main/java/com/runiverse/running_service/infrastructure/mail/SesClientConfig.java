package com.runiverse.running_service.infrastructure.mail;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sesv2.SesV2Client;

import java.time.Duration;

@Configuration
public class SesClientConfig {

    private static final Duration API_CALL_TIMEOUT = Duration.ofSeconds(5);

    @Bean
    public SesV2Client sesV2Client(SesProperties properties) {
        return SesV2Client.builder()
                .region(Region.of(properties.region()))
                .credentialsProvider(credentialsProvider(properties))
                .overrideConfiguration(c -> c.apiCallTimeout(API_CALL_TIMEOUT))
                .build();
    }

    /**
     * 로컬에서는 .env에 넣어둔 SES 전용 IAM 사용자 키를 쓰고,
     * 키가 비어 있는 배포 환경에서는 기본 체인(ECS/EC2 IAM Role)으로 넘긴다.
     */
    private AwsCredentialsProvider credentialsProvider(SesProperties properties) {
        if (StringUtils.hasText(properties.accessKeyId()) && StringUtils.hasText(properties.secretAccessKey())) {
            return StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(properties.accessKeyId(), properties.secretAccessKey()));
        }
        return DefaultCredentialsProvider.builder().build();

    }
}
