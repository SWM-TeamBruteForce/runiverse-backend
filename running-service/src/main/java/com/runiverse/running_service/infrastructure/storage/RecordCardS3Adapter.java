package com.runiverse.running_service.infrastructure.storage;

import com.runiverse.running_service.application.common.port.out.UploadedImage;
import com.runiverse.running_service.application.recordcard.port.out.CompleteMultipartUploadPort;
import com.runiverse.running_service.application.recordcard.port.out.GenerateRecordCardUploadUrlPort;
import com.runiverse.running_service.application.recordcard.port.out.LoadRecordCardImagePort;
import com.runiverse.running_service.application.recordcard.port.out.MultipartUpload;
import com.runiverse.running_service.application.recordcard.port.out.StartMultipartUploadPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CompletedPart;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.NoSuchUploadException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.UploadPartRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

// 기록 카드 사진·음악 버킷 — 프로필·배경 사진(user-assets)과 버킷을 나눠 AI 서버 권한·수명 주기 규칙을 따로 둔다
@Component
@RequiredArgsConstructor
public class RecordCardS3Adapter implements GenerateRecordCardUploadUrlPort, StartMultipartUploadPort,
        LoadRecordCardImagePort, CompleteMultipartUploadPort {

    private final S3Presigner s3Presigner;
    private final S3Properties properties;
    private final S3Client s3Client;

    @Override
    public String generate(String key, String contentType, long sizeBytes, Duration urlTtl) {
        // 형식·크기를 서명에 넣어 클라가 다른 값으로 올리지 못하게 막는다
        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(properties.recordCardBucket())
                .key(key)
                .contentType(contentType)
                .contentLength(sizeBytes)
                .build();
        return s3Presigner.presignPutObject(builder -> builder
                        .signatureDuration(urlTtl)
                        .putObjectRequest(putObjectRequest))
                .url()
                .toString();
    }

    @Override
    public MultipartUpload start(String key, String contentType, long fileSizeBytes, long partSizeBytes,
                                 Duration urlTtl) {
        // 1. 나눠 올리기를 시작한다 — 조각에는 Content-Type이 없어 합친 사진의 형식은 여기서 정해진다
        String uploadId = s3Client.createMultipartUpload(request -> request
                        .bucket(properties.recordCardBucket())
                        .key(key)
                        .contentType(contentType))
                .uploadId();

        // 2. 조각마다 URL을 서명한다 — 크기를 서명에 넣어 앱이 다르게 자르면 S3가 거절한다. 마지막 조각만 작다
        int partCount = Math.toIntExact(Math.ceilDiv(fileSizeBytes, partSizeBytes));
        List<MultipartUpload.Part> parts = new ArrayList<>(partCount);
        for (int partNumber = 1; partNumber <= partCount; partNumber++) {
            long offset = (partNumber - 1) * partSizeBytes;
            long contentLength = Math.min(partSizeBytes, fileSizeBytes - offset);
            UploadPartRequest uploadPartRequest = UploadPartRequest.builder()
                    .bucket(properties.recordCardBucket())
                    .key(key)
                    .uploadId(uploadId)
                    .partNumber(partNumber)
                    .contentLength(contentLength)
                    .build();
            String url = s3Presigner.presignUploadPart(builder -> builder
                            .signatureDuration(urlTtl)
                            .uploadPartRequest(uploadPartRequest))
                    .url()
                    .toString();
            parts.add(new MultipartUpload.Part(partNumber, url));
        }
        return new MultipartUpload(uploadId, parts);
    }

    @Override
    public Optional<UploadedImage> load(String imageKey) {
        try {
            HeadObjectResponse head = s3Client.headObject(request -> request
                    .bucket(properties.recordCardBucket())
                    .key(imageKey));
            return Optional.of(new UploadedImage(head.contentLength(), head.contentType()));
        } catch (NoSuchKeyException e) {
            return Optional.empty();
        } catch (S3Exception e) {
            // HEAD는 본문이 없어 NoSuchKey 대신 404 상태만 오기도 한다
            if (e.statusCode() == 404) {
                return Optional.empty();
            }
            throw e;
        }
    }

    @Override
    public MultipartCompletion complete(String imageKey, String uploadId, int expectedPartCount) {
        try {
            // 1. 올라온 조각을 읽는다 — 앱이 ETag를 모아 보내지 않아도 서버가 직접 읽는다
            List<CompletedPart> parts = s3Client.listPartsPaginator(request -> request
                            .bucket(properties.recordCardBucket())
                            .key(imageKey)
                            .uploadId(uploadId))
                    .parts().stream()
                    .map(part -> CompletedPart.builder().partNumber(part.partNumber()).eTag(part.eTag()).build())
                    .sorted(Comparator.comparingInt(CompletedPart::partNumber))
                    .toList();
            if (parts.size() < expectedPartCount) {
                return MultipartCompletion.PARTS_MISSING;
            }

            // 2. 다 있으면 합친다 — 합친 객체의 형식은 시작할 때 넣은 Content-Type이다
            s3Client.completeMultipartUpload(request -> request
                    .bucket(properties.recordCardBucket())
                    .key(imageKey)
                    .uploadId(uploadId)
                    .multipartUpload(upload -> upload.parts(parts)));
            return MultipartCompletion.COMPLETED;
        } catch (NoSuchUploadException e) {
            // 이미 합쳤거나(합침 표시 전에 끊긴 재시도) 수명 주기 규칙이 지웠다 — 어느 쪽인지는 HEAD가 가른다
            return MultipartCompletion.UPLOAD_NOT_FOUND;
        }
    }
}
