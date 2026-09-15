package com.runiverse.running_service.integration_test.fake;

import com.runiverse.running_service.application.user.port.out.DeleteProfileImagesPort;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

// 프로필 사진 객체 저장소. 키 하나가 아니라 프리픽스 단위로 지운다
public class FakeProfileImageStorage implements DeleteProfileImagesPort {

    private final Set<String> keys = new LinkedHashSet<>();
    private final List<String> deletedPrefixes = new ArrayList<>();

    @Override
    public void deleteAllByPrefix(String keyPrefix) {
        deletedPrefixes.add(keyPrefix);
        keys.removeIf(key -> key.startsWith(keyPrefix));
    }

    // 준비 전용
    public void put(String key) {
        keys.add(key);
    }

    // 검증 전용
    public boolean contains(String key) {
        return keys.contains(key);
    }

    public List<String> deletedPrefixes() {
        return List.copyOf(deletedPrefixes);
    }
}
