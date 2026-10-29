package com.runiverse.running_service.unit_test.device.domain;

import com.github.f4b6a3.uuid.UuidCreator;
import com.runiverse.running_service.domain.common.exception.InvalidVersionNameFormatException;
import com.runiverse.running_service.domain.common.exception.PlatformRequiredException;
import com.runiverse.running_service.domain.common.vo.Platform;
import com.runiverse.running_service.domain.device.UserDevice;
import com.runiverse.running_service.domain.device.exception.DeviceIdRequiredException;
import com.runiverse.running_service.domain.device.exception.PushTokenRequiredException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("유저 기기 애그리거트 단위 테스트")
public class UserDeviceTest {

    private final UUID userId = UuidCreator.getTimeOrderedEpoch();

    @Test
    @DisplayName("등록하면 받은 값을 VO로 감싸 담는다")
    void registerSuccess() {
        // when
        UserDevice device = UserDevice.register(userId, "device-1", "token-1", Platform.ANDROID, "1.1.0");

        // then
        assertThat(device.getUserId().value()).isEqualTo(userId);
        assertThat(device.getDeviceId().value()).isEqualTo("device-1");
        assertThat(device.getPushToken().value()).isEqualTo("token-1");
        assertThat(device.getPlatform()).isEqualTo(Platform.ANDROID);
        assertThat(device.getAppVersion().value()).isEqualTo("1.1.0");
    }

    @Test
    @DisplayName("플랫폼이 없으면 필수 예외가 발생한다")
    void nullPlatformFails() {
        // when & then
        assertThatThrownBy(() -> UserDevice.register(userId, "device-1", "token-1", null, "1.1.0"))
                .isInstanceOf(PlatformRequiredException.class);
    }

    @Test
    @DisplayName("값 규칙은 각 VO가 검사한다")
    void delegatesValidationToVo() {
        // when & then
        assertThatThrownBy(() -> UserDevice.register(userId, " ", "token-1", Platform.IOS, "1.1.0"))
                .isInstanceOf(DeviceIdRequiredException.class);
        assertThatThrownBy(() -> UserDevice.register(userId, "device-1", "", Platform.IOS, "1.1.0"))
                .isInstanceOf(PushTokenRequiredException.class);
        assertThatThrownBy(() -> UserDevice.register(userId, "device-1", "token-1", Platform.IOS, "1.1"))
                .isInstanceOf(InvalidVersionNameFormatException.class);
    }
}
