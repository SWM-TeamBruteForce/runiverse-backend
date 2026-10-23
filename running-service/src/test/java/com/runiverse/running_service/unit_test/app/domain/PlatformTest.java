package com.runiverse.running_service.unit_test.app.domain;

import com.runiverse.running_service.domain.common.exception.PlatformRequiredException;
import com.runiverse.running_service.domain.common.exception.UnsupportedPlatformException;
import com.runiverse.running_service.domain.common.vo.Platform;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class PlatformTest {

    @ParameterizedTest
    @ValueSource(strings = {"IOS", "ios", "Ios", " IOS "})
    @DisplayName("대소문자와 앞뒤 공백을 가리지 않고 IOS로 읽는다")
    void readsIosIgnoringCase(String value) {
        // when & then
        assertThat(Platform.from(value)).isEqualTo(Platform.IOS);
    }

    @Test
    @DisplayName("ANDROID도 대소문자를 가리지 않는다")
    void readsAndroidIgnoringCase() {
        // when & then
        assertThat(Platform.from("android")).isEqualTo(Platform.ANDROID);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    @DisplayName("비어 있으면 필수 예외가 발생한다")
    void blankFails(String value) {
        // when & then
        assertThatThrownBy(() -> Platform.from(value))
                .isInstanceOf(PlatformRequiredException.class)
                .hasMessage("플랫폼은 필수입니다.");
    }

    @Test
    @DisplayName("null이면 필수 예외가 발생한다")
    void nullFails() {
        // when & then
        assertThatThrownBy(() -> Platform.from(null))
                .isInstanceOf(PlatformRequiredException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"WEB", "IPADOS", "AOS"})
    @DisplayName("정해진 값이 아니면 미지원 예외가 발생한다")
    void unknownFails(String value) {
        // when & then
        assertThatThrownBy(() -> Platform.from(value))
                .isInstanceOf(UnsupportedPlatformException.class)
                .hasMessage("플랫폼은 IOS 또는 ANDROID이어야 합니다.");
    }
}
