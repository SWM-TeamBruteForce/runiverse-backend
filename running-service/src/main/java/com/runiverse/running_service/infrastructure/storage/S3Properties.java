package com.runiverse.running_service.infrastructure.storage;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@ConfigurationProperties(prefix = "storage.s3")
@Validated
public record S3Properties(
        @NotBlank String region,
        @NotBlank String userAssetBucket,   // 프로필·피드 이미지 (presigned, 클라 직접 접근)
        @NotBlank String gpsTrackBucket,    // GPS 원본 트랙 (서버 전용)
        @NotBlank String recordCardBucket,  // 기록 카드 사진·음악 (presigned, 클라 직접 업로드 + AI 서버 접근)
        @NotNull Duration presignedUrlTtl,
        @NotNull Duration viewUrlTtl,
        String accessKeyId,      // 비우면 기본 자격증명 체인(IAM Role) 사용
        String secretAccessKey
) {

}
