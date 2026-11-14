package com.runiverse.running_service.integration_test.fake;

import com.runiverse.running_service.application.recordcard.port.out.MultipartUpload;
import com.runiverse.running_service.application.recordcard.port.out.StartMultipartUploadPort;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

// S3 나눠 올리기 시작 — 조각 수는 실제 어댑터와 같이 크기 ÷ 조각 크기 올림이다
public class FakeMultipartUploadStarter implements StartMultipartUploadPort {

    public record Started(String key, String contentType, long fileSizeBytes, long partSizeBytes, Duration urlTtl) {

    }

    private final List<Started> started = new ArrayList<>();

    @Override
    public MultipartUpload start(String key, String contentType, long fileSizeBytes, long partSizeBytes,
                                 Duration urlTtl) {
        started.add(new Started(key, contentType, fileSizeBytes, partSizeBytes, urlTtl));
        int partCount = Math.toIntExact(Math.ceilDiv(fileSizeBytes, partSizeBytes));
        List<MultipartUpload.Part> parts = new ArrayList<>();
        for (int partNumber = 1; partNumber <= partCount; partNumber++) {
            parts.add(new MultipartUpload.Part(partNumber, "https://fake/%s?partNumber=%d".formatted(key, partNumber)));
        }
        return new MultipartUpload(uploadIdOf(key), parts);
    }

    // 아래는 검증 전용
    public List<Started> started() {
        return List.copyOf(started);
    }

    public String uploadIdOf(String key) {
        return "upload-" + key;
    }
}
