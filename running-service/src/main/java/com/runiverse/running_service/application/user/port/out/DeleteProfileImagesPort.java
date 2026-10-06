package com.runiverse.running_service.application.user.port.out;

public interface DeleteProfileImagesPort {

    // 사진을 바꿀 때마다 객체가 쌓이므로 키 하나가 아니라 프리픽스 전체를 지운다
    void deleteAllByPrefix(String keyPrefix);
}
