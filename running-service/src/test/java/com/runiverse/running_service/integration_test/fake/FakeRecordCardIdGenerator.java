package com.runiverse.running_service.integration_test.fake;

import com.runiverse.running_service.application.recordcard.port.out.GenerateRecordCardDraftIdPort;
import com.runiverse.running_service.application.recordcard.port.out.GenerateRecordCardIdPort;

import java.util.UUID;

// 초안 ID·카드 ID — 값이 예측 가능해야 검증할 수 있다. 종류마다 인스턴스를 따로 쓰고 상위 비트로 가른다
public class FakeRecordCardIdGenerator implements GenerateRecordCardDraftIdPort, GenerateRecordCardIdPort {

    private final long tag;
    private int sequence = 0;

    public FakeRecordCardIdGenerator(long tag) {
        this.tag = tag;
    }

    @Override
    public UUID generate() {
        return new UUID(tag, ++sequence);
    }

    public UUID idAt(int sequence) {
        return new UUID(tag, sequence);
    }
}
