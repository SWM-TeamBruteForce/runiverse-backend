package com.runiverse.running_service.unit_test.device.domain;

import com.runiverse.running_service.domain.device.exception.DeviceIdRequiredException;
import com.runiverse.running_service.domain.device.exception.InvalidDeviceIdLengthException;
import com.runiverse.running_service.domain.device.vo.DeviceId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("기기 ID VO 단위 테스트")
public class DeviceIdTest {

    @ParameterizedTest
    @ValueSource(strings = {"3f1c2a9e-7b4d-4e8a-9c21-5d6f0b8e1a37", "android-id-1234", "a"})
    @DisplayName("형식과 무관하게 비어 있지 않으면 만들 수 있다")
    void createDeviceIdSuccess(String value) {
        // when & then -> 앱이 정하는 값이라 UUID라고 가정하지 않는다
        assertThat(new DeviceId(value).value()).isEqualTo(value);
    }

    @Test
    @DisplayName("100자까지 만들 수 있다")
    void maxLengthSucceeds() {
        // given
        String value = "a".repeat(100);

        // when & then
        assertThat(new DeviceId(value).value()).isEqualTo(value);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   "})
    @DisplayName("없거나 비어 있으면 필수 예외가 발생한다")
    void blankFails(String value) {
        // when & then
        assertThatThrownBy(() -> new DeviceId(value))
                .isInstanceOf(DeviceIdRequiredException.class)
                .hasMessage("기기 ID는 필수입니다.");
    }

    @Test
    @DisplayName("100자를 넘으면 길이 예외가 발생한다")
    void tooLongFails() {
        // when & then -> 컬럼 길이를 넘는 값을 DB까지 보내지 않는다
        assertThatThrownBy(() -> new DeviceId("a".repeat(101)))
                .isInstanceOf(InvalidDeviceIdLengthException.class)
                .hasMessage("기기 ID는 100자 이하여야 합니다.");
    }
}
