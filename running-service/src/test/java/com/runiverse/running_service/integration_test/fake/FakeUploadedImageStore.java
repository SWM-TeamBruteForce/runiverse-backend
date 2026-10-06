package com.runiverse.running_service.integration_test.fake;

import com.runiverse.running_service.application.user.port.out.LoadUploadedImagePort;
import com.runiverse.running_service.application.user.port.out.UploadedImage;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

// S3에 실제로 올라간 객체를 흉내 낸다. 등록하지 않은 key는 "업로드되지 않음"이다
public class FakeUploadedImageStore implements LoadUploadedImagePort {

    private final Map<String, UploadedImage> uploaded = new HashMap<>();

    public void register(String key, long sizeBytes, String contentType) {
        uploaded.put(key, new UploadedImage(sizeBytes, contentType));
    }

    @Override
    public Optional<UploadedImage> load(String key) {
        return Optional.ofNullable(uploaded.get(key));
    }
}
