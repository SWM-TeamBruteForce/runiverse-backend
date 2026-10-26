package com.runiverse.running_service.unit_test.device.application;

import com.github.f4b6a3.uuid.UuidCreator;
import com.runiverse.running_service.application.device.command.register.RegisterDeviceCommand;
import com.runiverse.running_service.application.device.command.register.RegisterDeviceHandler;
import com.runiverse.running_service.application.device.port.out.DeactivateUserDevicesPort;
import com.runiverse.running_service.application.device.port.out.UpsertUserDevicePort;
import com.runiverse.running_service.domain.common.exception.UnsupportedPlatformException;
import com.runiverse.running_service.domain.common.vo.Platform;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.device.UserDevice;
import com.runiverse.running_service.domain.device.vo.DeviceId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@DisplayName("기기 등록 단위 테스트")
public class RegisterDeviceHandlerTest {

    @Mock
    private UpsertUserDevicePort upsertUserDevicePort;
    @Mock
    private DeactivateUserDevicesPort deactivateUserDevicesPort;

    @InjectMocks
    private RegisterDeviceHandler handler;

    private final UUID userId = UuidCreator.getTimeOrderedEpoch();

    @Test
    @DisplayName("이 기기를 켠 뒤 같은 유저의 다른 기기를 끈다")
    void upsertsThenDeactivatesOthers() {
        // when
        handler.handle(new RegisterDeviceCommand(userId, "device-1", "token-1", "IOS", "1.1.0"));

        // then -> 순서가 바뀌면 끄는 쿼리가 방금 켠 기기를 빼지 못한 채 돌 수 있다
        ArgumentCaptor<UserDevice> captor = ArgumentCaptor.forClass(UserDevice.class);
        InOrder order = inOrder(upsertUserDevicePort, deactivateUserDevicesPort);
        order.verify(upsertUserDevicePort).upsert(captor.capture());
        order.verify(deactivateUserDevicesPort).deactivateOthers(new UserId(userId), new DeviceId("device-1"));

        UserDevice saved = captor.getValue();
        assertThat(saved.getUserId().value()).isEqualTo(userId);
        assertThat(saved.getDeviceId().value()).isEqualTo("device-1");
        assertThat(saved.getPushToken().value()).isEqualTo("token-1");
        assertThat(saved.getPlatform()).isEqualTo(Platform.IOS);
        assertThat(saved.getAppVersion().value()).isEqualTo("1.1.0");
    }

    @Test
    @DisplayName("플랫폼은 대소문자를 가리지 않고 대문자 값으로 저장한다")
    void normalizesPlatform() {
        // when -> 버전 검사 필터가 소문자 헤더도 통과시킨다
        handler.handle(new RegisterDeviceCommand(userId, "device-1", "token-1", "android", "1.1.0"));

        // then
        ArgumentCaptor<UserDevice> captor = ArgumentCaptor.forClass(UserDevice.class);
        verify(upsertUserDevicePort).upsert(captor.capture());
        assertThat(captor.getValue().getPlatform()).isEqualTo(Platform.ANDROID);
    }

    @Test
    @DisplayName("값이 규칙에 어긋나면 아무것도 저장하지 않는다")
    void invalidValueSavesNothing() {
        // when & then
        assertThatThrownBy(() -> handler.handle(
                new RegisterDeviceCommand(userId, "device-1", "token-1", "WEB", "1.1.0")))
                .isInstanceOf(UnsupportedPlatformException.class);
        verifyNoInteractions(upsertUserDevicePort, deactivateUserDevicesPort);
    }
}
