package com.runiverse.running_service.unit_test.app.domain;

import com.runiverse.running_service.domain.common.exception.InvalidVersionNameFormatException;
import com.runiverse.running_service.domain.common.exception.VersionNameRequiredException;
import com.runiverse.running_service.domain.common.vo.VersionName;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class VersionNameTest {

    @ParameterizedTest
    @ValueSource(strings = {"1.0.0", "0.0.1", "10.20.30", "999999999.0.0"})
    @DisplayName("숫자 세 부분이면 만들 수 있다")
    void createVersionNameSuccess(String value) {
        // when & then
        assertThat(new VersionName(value).value()).isEqualTo(value);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "1", "1.0", "1.0.0.0", "v1.0.0", "1.0.0-beta", "1.0.0+10", " 1.0.0", "1..0",
            "1000000000.0.0"})
    @DisplayName("MAJOR.MINOR.PATCH가 아니면 형식 예외가 발생한다")
    void invalidFormatFails(String value) {
        // when & then -> 10자리 부분은 정수로 바꿀 때 넘칠 수 있어 받지 않는다
        assertThatThrownBy(() -> new VersionName(value))
                .isInstanceOf(InvalidVersionNameFormatException.class)
                .hasMessage("버전 이름은 MAJOR.MINOR.PATCH 형식이어야 합니다.");
    }

    @Test
    @DisplayName("null이면 필수 예외가 발생한다")
    void nullFails() {
        // when & then
        assertThatThrownBy(() -> new VersionName(null))
                .isInstanceOf(VersionNameRequiredException.class)
                .hasMessage("버전 이름은 필수입니다.");
    }

    @ParameterizedTest
    @CsvSource({
            "1.0.0, 1.0.1",
            "1.0.9, 1.1.0",
            "1.9.9, 2.0.0",
            // 글자로 비교하면 거꾸로 나온다
            "1.9.0, 1.10.0",
            "1.0.2, 1.0.10"
    })
    @DisplayName("부분마다 정수로 비교한다")
    void comparesEachPartAsNumber(String lower, String higher) {
        // when & then
        assertThat(new VersionName(lower).isLowerThan(new VersionName(higher))).isTrue();
        assertThat(new VersionName(higher).isLowerThan(new VersionName(lower))).isFalse();
    }

    @Test
    @DisplayName("같은 버전은 낮지 않다")
    void sameVersionIsNotLower() {
        // when & then -> 최소 버전과 같은 앱은 통과해야 한다
        assertThat(new VersionName("1.1.0").isLowerThan(new VersionName("1.1.0"))).isFalse();
    }

    @Test
    @DisplayName("앞자리 0은 숫자로 읽는다")
    void leadingZeroIsNumber() {
        // when & then
        assertThat(new VersionName("1.01.0").isLowerThan(new VersionName("1.1.0"))).isFalse();
        assertThat(new VersionName("1.1.0").isLowerThan(new VersionName("1.01.0"))).isFalse();
    }
}
