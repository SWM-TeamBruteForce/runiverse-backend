package com.runiverse.running_service.integration_test.fake;

import com.runiverse.running_service.application.user.port.out.GenerateProfileImageIdPort;

import java.util.UUID;

public class FakeProfileImageIdGenerator implements GenerateProfileImageIdPort {

    private int sequence = 0;

    // key가 호출마다 달라지는지 보려면 값이 예측 가능해야 한다
    @Override
    public UUID generate() {
        return new UUID(0L, ++sequence);
    }
}
