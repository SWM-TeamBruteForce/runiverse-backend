package com.runiverse.running_service.infrastructure.storage;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.time.Duration;

@Configuration
public class S3ClientConfig {

    @Bean
    public S3Presigner s3Presigner(S3Properties properties) {
        return S3Presigner.builder()
                .region(Region.of(properties.region()))
                .credentialsProvider(credentialsProvider(properties))
                .build();
    }

    @Bean
    public S3Client s3Client(S3Properties properties) {
        return S3Client.builder()
                .region(Region.of(properties.region()))
                .credentialsProvider(credentialsProvider(properties))
                .overrideConfiguration(c -> c.apiCallAttemptTimeout(Duration.ofSeconds(3)))
                .build();
    }

    // 기존 테스트 서버나 prod 서버에 경우 ec2에 권한이 있기 때문에 access_key가 필요 없다.
    private AwsCredentialsProvider credentialsProvider(S3Properties properties) {
        if (StringUtils.hasText(properties.accessKeyId()) && StringUtils.hasText(properties.secretAccessKey())) {
            return StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(properties.accessKeyId(), properties.secretAccessKey()));
        }
        return DefaultCredentialsProvider.builder().build();
    }
}
