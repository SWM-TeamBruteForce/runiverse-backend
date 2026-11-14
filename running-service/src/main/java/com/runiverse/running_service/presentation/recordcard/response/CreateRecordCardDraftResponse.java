package com.runiverse.running_service.presentation.recordcard.response;

import com.runiverse.running_service.application.recordcard.command.createdraft.CreateRecordCardDraftResult;
import com.runiverse.running_service.application.recordcard.command.createdraft.CreateRecordCardDraftResult.Upload;
import com.runiverse.running_service.application.recordcard.port.out.MultipartUpload;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record CreateRecordCardDraftResponse(
        UUID recordCardDraftId,
        LocalDateTime expiresAt,
        List<UploadResponse> uploads
) {

    public static CreateRecordCardDraftResponse from(CreateRecordCardDraftResult result) {
        return new CreateRecordCardDraftResponse(result.recordCardDraftId(), result.expiresAt(),
                result.uploads().stream().map(UploadResponse::from).toList());
    }

    // 올리는 방식에 따라 채워지는 필드가 다르다 — 키는 null이어도 항상 내려간다(api-convention)
    public record UploadResponse(
            UUID recordCardImageId,     // 작성·수정이 같은 확정을 쓰므로 앱은 순서가 아니라 이 ID로 사진을 맞춘다
            String imageKey,
            String uploadType,          // SINGLE · MULTIPART
            String uploadUrl,           // SINGLE일 때만
            Long partSizeBytes,         // MULTIPART일 때만
            List<PartResponse> parts    // MULTIPART일 때만
    ) {

        static UploadResponse from(Upload upload) {
            return switch (upload) {
                case Upload.Single single -> new UploadResponse(single.recordCardImageId(), single.imageKey(),
                        "SINGLE", single.uploadUrl(), null, null);
                case Upload.Multipart multipart -> new UploadResponse(multipart.recordCardImageId(),
                        multipart.imageKey(), "MULTIPART", null, multipart.partSizeBytes(),
                        multipart.parts().stream().map(PartResponse::from).toList());
            };
        }
    }

    public record PartResponse(int partNumber, String uploadUrl) {

        static PartResponse from(MultipartUpload.Part part) {
            return new PartResponse(part.partNumber(), part.uploadUrl());
        }
    }
}
