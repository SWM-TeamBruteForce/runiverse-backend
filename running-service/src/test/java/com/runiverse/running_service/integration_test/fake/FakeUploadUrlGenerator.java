package com.runiverse.running_service.integration_test.fake;

import com.runiverse.running_service.application.common.port.out.GenerateUploadUrlPort;
import com.runiverse.running_service.application.recordcard.port.out.GenerateRecordCardUploadUrlPort;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

// 프로필·배경(user-assets)과 기록 카드 버킷의 업로드 URL — 버킷만 다르고 서명 방식은 같아 하나로 흉내 낸다
public class FakeUploadUrlGenerator implements GenerateUploadUrlPort, GenerateRecordCardUploadUrlPort {

    public record IssuedUrl(String key, String contentType, long sizeBytes) {

    }

    private static final String BASE_URL = "https://fake-bucket.s3.ap-northeast-2.amazonaws.com/";
    private static final String QUERY = "?X-Amz-Signature=fake-signature";

    private final List<IssuedUrl> issued = new ArrayList<>();

    @Override
    public String generate(String key, String contentType, long sizeBytes) {
        issued.add(new IssuedUrl(key, contentType, sizeBytes));
        return urlOf(key);
    }

    // 기록 카드 버킷 — 유효 시간만 더 받는다
    @Override
    public String generate(String key, String contentType, long sizeBytes, Duration urlTtl) {
        return generate(key, contentType, sizeBytes);
    }

    // 아래는 검증 전용
    public boolean isEmpty() {
        return issued.isEmpty();
    }

    public IssuedUrl last() {
        return issued.get(issued.size() - 1);
    }

    public String urlOf(String key) {
        return BASE_URL + key + QUERY;
    }
}
