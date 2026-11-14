package com.runiverse.running_service.application.recordcard.port.out;

import java.time.Duration;

public interface StartMultipartUploadPort {

    // 나눠 올리기를 시작하고 조각마다 업로드 URL을 발급한다.
    // 조각 수는 fileSizeBytes ÷ partSizeBytes 올림이고, 마지막 조각만 작다.
    // 조각마다 크기를 서명에 넣어, 앱이 다른 크기로 올리면 S3가 거절한다
    MultipartUpload start(String key, String contentType, long fileSizeBytes, long partSizeBytes, Duration urlTtl);
}
