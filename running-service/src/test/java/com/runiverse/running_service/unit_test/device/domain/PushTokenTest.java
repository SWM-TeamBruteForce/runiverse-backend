package com.runiverse.running_service.unit_test.device.domain;

import com.runiverse.running_service.domain.device.exception.PushTokenRequiredException;
import com.runiverse.running_service.domain.device.vo.PushToken;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("푸시 토큰 VO 단위 테스트")
public class PushTokenTest {

    @Test
    @DisplayName("길이 상한 없이 만들 수 있다")
    void longTokenSucceeds() {
        // given -> FCM 토큰은 공식 길이 상한이 없다
        String value = "t".repeat(5000);

        // when & then
        assertThat(new PushToken(value).value()).isEqualTo(value);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   "})
    @DisplayName("없거나 비어 있으면 필수 예외가 발생한다")
    void blankFails(String value) {
        // when & then
        assertThatThrownBy(() -> new PushToken(value))
                .isInstanceOf(PushTokenRequiredException.class)
                .hasMessage("푸시 토큰은 필수입니다.");
    }
}
