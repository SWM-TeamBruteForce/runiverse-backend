package com.runiverse.running_service.unit_test.infrastructure.identifier;

import com.runiverse.running_service.infrastructure.identifier.UuidGeneratorAdapter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

public class UuidGeneratorAdapterTest {

    private final UuidGeneratorAdapter uuidGeneratorAdapter = new UuidGeneratorAdapter();

    @Test
    @DisplayName("사용자 ID를 생성하면 UUID를 반환한다")
    void generateUserId() {
        // when
        UUID userId = uuidGeneratorAdapter.generate();

        // then
        assertThat(userId).isNotNull();
    }

    @Test
    @DisplayName("사용자 ID를 여러 번 생성하면 서로 다른 UUID를 반환한다")
    void generateDifferentUserIds() {
        // when
        UUID firstUserId = uuidGeneratorAdapter.generate();
        UUID secondUserId = uuidGeneratorAdapter.generate();

        // then
        assertThat(firstUserId).isNotEqualTo(secondUserId);
    }

    @Test
    @DisplayName("생성된 사용자 ID는 UUID 버전 7이다")
    void generateUuidVersion7() {
        // when
        UUID userId = uuidGeneratorAdapter.generate();

        // then
        assertThat(userId.version()).isEqualTo(7);
    }

}
