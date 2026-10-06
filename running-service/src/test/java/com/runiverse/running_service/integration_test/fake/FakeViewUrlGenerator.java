package com.runiverse.running_service.integration_test.fake;

import com.runiverse.running_service.application.user.port.out.GenerateViewUrlPort;

import java.util.ArrayList;
import java.util.List;

public class FakeViewUrlGenerator implements GenerateViewUrlPort {

    private static final String BASE_URL = "https://fake-bucket.s3.ap-northeast-2.amazonaws.com/";
    private static final String QUERY = "?X-Amz-Signature=fake-signature";

    private final List<String> issued = new ArrayList<>();

    @Override
    public String generate(String key) {
        issued.add(key);
        return urlOf(key);
    }

    // 아래는 검증 전용
    public boolean isEmpty() {
        return issued.isEmpty();
    }

    public String urlOf(String key) {
        return BASE_URL + key + QUERY;
    }
}
