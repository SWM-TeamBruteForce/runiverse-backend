package com.runiverse.running_service.integration_test.fake;

import com.runiverse.running_service.application.auth.port.out.GenerateVerificationCodePort;

// 무작위면 테스트가 정답 코드를 알 수 없다. 순번으로 예측 가능하게 만든다
public class FakeVerificationCodeGenerator implements GenerateVerificationCodePort {

    private int sequence = 0;
    private String lastGenerated;

    @Override
    public String generate() {
        lastGenerated = String.format("%06d", ++sequence);
        return lastGenerated;
    }

    // 검증 전용 - 방금 메일로 나간 원문 코드
    public String lastGenerated() {
        return lastGenerated;
    }
}
