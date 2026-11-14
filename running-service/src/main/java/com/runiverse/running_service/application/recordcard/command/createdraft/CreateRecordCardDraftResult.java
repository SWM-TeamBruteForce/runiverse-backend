package com.runiverse.running_service.application.recordcard.command.createdraft;

import com.runiverse.running_service.application.recordcard.port.out.MultipartUpload;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record CreateRecordCardDraftResult(
        UUID recordCardDraftId,
        LocalDateTime expiresAt,    // 이 시각까지 9-2로 확정해야 한다
        List<Upload> uploads        // 요청한 사진 순서대로 — 사진이 없으면 빈 목록
) {

    public CreateRecordCardDraftResult {
        uploads = List.copyOf(uploads);
    }

    // 사진 하나를 올릴 곳 — 크기에 따라 둘 중 하나다
    // 사진 ID를 함께 준다 — 앱이 업로드 결과를 자기 사진과 맞추고, 수정(9-3)에서 이 사진을 가리킬 때 쓴다
    public sealed interface Upload {

        UUID recordCardImageId();

        String imageKey();

        // 한 번에 올린다 — URL 하나
        record Single(UUID recordCardImageId, String imageKey, String uploadUrl) implements Upload {

        }

        // 조각으로 나눠 올린다 — S3 UploadId는 초안에만 두고 여기 싣지 않는다
        record Multipart(UUID recordCardImageId, String imageKey, long partSizeBytes, List<MultipartUpload.Part> parts)
                implements Upload {

            public Multipart {
                parts = List.copyOf(parts);
            }
        }
    }
}
