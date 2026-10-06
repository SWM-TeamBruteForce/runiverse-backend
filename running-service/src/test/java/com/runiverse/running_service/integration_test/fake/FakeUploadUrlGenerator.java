package com.runiverse.running_service.integration_test.fake;

import com.runiverse.running_service.application.user.port.out.GenerateUploadUrlPort;

import java.util.ArrayList;
import java.util.List;

public class FakeUploadUrlGenerator implements GenerateUploadUrlPort {

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
