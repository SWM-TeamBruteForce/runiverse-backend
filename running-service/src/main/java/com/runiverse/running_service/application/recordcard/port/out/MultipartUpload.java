package com.runiverse.running_service.application.recordcard.port.out;

import java.util.List;

// 나눠 올리기를 시작한 결과 — uploadId는 초안에만 두고 앱에는 조각 URL만 준다
public record MultipartUpload(
        String uploadId,        // S3가 준 Multipart UploadId — 확정 때 조각 확인·합치기에 쓴다
        List<Part> parts        // partNumber 오름차순
) {

    public MultipartUpload {
        parts = List.copyOf(parts);
    }

    // 조각 하나 — 앱은 파일을 잘라 partNumber에 맞는 URL로 PUT한다
    public record Part(int partNumber, String uploadUrl) {

    }
}
