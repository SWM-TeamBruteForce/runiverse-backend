package com.runiverse.running_service.application.recordcard.common;

import com.runiverse.running_service.application.common.ImageContentType;
import com.runiverse.running_service.application.common.port.out.GenerateImageIdPort;
import com.runiverse.running_service.application.recordcard.command.createdraft.CreateRecordCardDraftResult.Upload;
import com.runiverse.running_service.application.recordcard.exception.RecordCardImageTooLargeException;
import com.runiverse.running_service.application.recordcard.exception.RecordCardThumbnailTooLargeException;
import com.runiverse.running_service.application.recordcard.port.out.GenerateRecordCardUploadUrlPort;
import com.runiverse.running_service.application.recordcard.port.out.MultipartUpload;
import com.runiverse.running_service.application.recordcard.port.out.RecordCardDraft.DraftImage;
import com.runiverse.running_service.application.recordcard.port.out.RecordCardDraft.UploadType;
import com.runiverse.running_service.application.recordcard.port.out.StartMultipartUploadPort;
import com.runiverse.running_service.domain.recordcard.vo.RecordCardImageCaption;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

// 새로 올릴 사진에 사진 ID·key를 정하고 업로드 URL을 준다 — 작성(9-1)과 수정(9-3)이 같은 규칙을 쓴다.
// 10MB 이하는 한 번에(presigned PUT), 넘으면 조각으로(S3 Multipart — UploadId는 초안에만 둔다). URL은 초안과 같은 시간이다.
// 사진마다 작은 버전(썸네일) URL을 하나 더 준다 — 달력·목록 칸이 원본 대신 받는다
@Component
@RequiredArgsConstructor
public class RecordCardUploadIssuer {

    private final RecordCardDraftProperties properties;
    private final GenerateImageIdPort generateImageIdPort;
    private final GenerateRecordCardUploadUrlPort generateRecordCardUploadUrlPort;
    private final StartMultipartUploadPort startMultipartUploadPort;

    // 올릴 사진 하나 — 아직 파일은 없고 형식·크기·이름·설명만 안다. 작은 버전(썸네일)은 원본과 같은 형식이라 크기만 안다
    public record NewImage(String mimeType, long fileSizeBytes, long thumbnailFileSizeBytes, String fileName,
                           String caption) {

    }

    // 초안에 둘 사진과 앱에 줄 업로드 정보
    public record Issued(DraftImage draftImage, Upload upload) {

    }

    // 운영값(최대 크기) 검사(400) — 형식·개수·글자 수는 Request DTO가 이미 봤다. 업로드 URL을 주기 전에 거른다
    public void rejectTooLarge(List<NewImage> images) {
        long maxBytes = properties.maxImageSize().toBytes();
        if (images.stream().anyMatch(image -> image.fileSizeBytes() > maxBytes)) {
            throw new RecordCardImageTooLargeException(properties.maxImageSize().toMegabytes());
        }
        long maxThumbnailBytes = properties.maxThumbnailSize().toBytes();
        if (images.stream().anyMatch(image -> image.thumbnailFileSizeBytes() > maxThumbnailBytes)) {
            throw new RecordCardThumbnailTooLargeException(properties.maxThumbnailSize().toMegabytes());
        }
    }

    public Issued issue(UUID userId, UUID recordCardId, NewImage image) {
        ImageContentType contentType = ImageContentType.from(image.mimeType());
        UUID recordCardImageId = generateImageIdPort.generate();
        String key = RecordCardStorageKeys.imageKey(userId, recordCardId, recordCardImageId, image.fileName());
        String caption = RecordCardImageCaption.ofNullable(image.caption())
                .map(RecordCardImageCaption::value)
                .orElse(null);
        Duration ttl = properties.ttl();
        long partSizeBytes = properties.partSize().toBytes();

        // 작은 버전(썸네일) — 원본과 같은 형식으로 늘 한 번에 올린다. key는 원본 key에서 계산해 저장하지 않는다
        String thumbnailKey = RecordCardStorageKeys.thumbnailKeyOf(key);
        Upload.Thumbnail thumbnail = new Upload.Thumbnail(thumbnailKey, generateRecordCardUploadUrlPort.generate(
                thumbnailKey, contentType.getMimeType(), image.thumbnailFileSizeBytes(), ttl));

        if (image.fileSizeBytes() <= partSizeBytes) {
            // 한 번에 올린다 — URL도 초안과 같은 시간을 준다
            String uploadUrl = generateRecordCardUploadUrlPort.generate(
                    key, contentType.getMimeType(), image.fileSizeBytes(), ttl);
            return new Issued(new DraftImage(recordCardImageId, key, contentType.getMimeType(), image.fileSizeBytes(),
                    caption, UploadType.SINGLE, null, null, false, image.thumbnailFileSizeBytes()),
                    new Upload.Single(recordCardImageId, key, uploadUrl, thumbnail));
        }

        // 조각으로 나눠 올린다 — UploadId는 초안에만 두고, 조각 URL은 초안과 같은 시간을 준다
        MultipartUpload multipart = startMultipartUploadPort.start(
                key, contentType.getMimeType(), image.fileSizeBytes(), partSizeBytes, ttl);
        return new Issued(new DraftImage(recordCardImageId, key, contentType.getMimeType(), image.fileSizeBytes(),
                caption, UploadType.MULTIPART, multipart.uploadId(), multipart.parts().size(), false,
                image.thumbnailFileSizeBytes()),
                new Upload.Multipart(recordCardImageId, key, partSizeBytes, multipart.parts(), thumbnail));
    }
}
