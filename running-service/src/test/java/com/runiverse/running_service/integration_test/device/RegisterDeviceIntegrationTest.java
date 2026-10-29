package com.runiverse.running_service.integration_test.device;

import com.github.f4b6a3.uuid.UuidCreator;
import com.runiverse.running_service.application.device.command.register.RegisterDeviceCommand;
import com.runiverse.running_service.application.device.command.register.RegisterDeviceHandler;
import com.runiverse.running_service.domain.common.vo.Platform;
import com.runiverse.running_service.domain.user.User;
import com.runiverse.running_service.domain.user.vo.Provider;
import com.runiverse.running_service.integration_test.fake.InMemoryUserDeviceStore;
import com.runiverse.running_service.integration_test.fake.InMemoryUserDeviceStore.StoredDevice;
import com.runiverse.running_service.integration_test.fake.InMemoryUserStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("기기 등록 통합 테스트")
public class RegisterDeviceIntegrationTest {

    private final UUID userId = UuidCreator.getTimeOrderedEpoch();
    private final UUID otherUserId = UuidCreator.getTimeOrderedEpoch();
    private InMemoryUserStore userStore;
    private InMemoryUserDeviceStore userDeviceStore;
    private RegisterDeviceHandler registerDeviceHandler;

    @BeforeEach
    void setUp() {
        userStore = new InMemoryUserStore();
        userStore.save(User.registerWithOauth(userId, "runner@runiverse.com", Provider.KAKAO, "kakao-1"));
        userStore.save(User.registerWithOauth(otherUserId, "other@runiverse.com", Provider.KAKAO, "kakao-2"));
        userDeviceStore = new InMemoryUserDeviceStore();
        registerDeviceHandler = new RegisterDeviceHandler(
                id -> { },         // LockUserPort — 한 스레드라 줄 세울 일이 없다
                userStore,         // ExistsUserPort
                userDeviceStore,   // UpsertUserDevicePort
                userDeviceStore    // DeactivateUserDevicesPort
        );
    }

    private void register(UUID userId, String deviceId, String pushToken) {
        registerDeviceHandler.handle(new RegisterDeviceCommand(userId, deviceId, pushToken, "IOS", "1.1.0"));
    }

    @Test
    @DisplayName("처음 등록하면 켜진 기기 하나가 생긴다")
    void firstRegistrationIsActive() {
        // when
        register(userId, "device-1", "token-1");

        // then
        StoredDevice device = userDeviceStore.find("device-1").orElseThrow();
        assertThat(device.userId()).isEqualTo(userId);
        assertThat(device.pushToken()).isEqualTo("token-1");
        assertThat(device.platform()).isEqualTo(Platform.IOS);
        assertThat(device.appVersion()).isEqualTo("1.1.0");
        assertThat(userDeviceStore.activeDeviceIds(userId)).containsExactly("device-1");
    }

    @Test
    @DisplayName("같은 요청을 다시 보내도 행이 늘지 않는다")
    void sameRequestDoesNotAddRow() {
        // given
        register(userId, "device-1", "token-1");

        // when -> 응답을 못 받은 앱이 다시 보낸 경우다
        register(userId, "device-1", "token-1");

        // then
        assertThat(userDeviceStore.size()).isEqualTo(1);
        assertThat(userDeviceStore.activeDeviceIds(userId)).containsExactly("device-1");
    }

    @Test
    @DisplayName("토큰이 바뀌면 같은 행의 토큰을 바꾼다")
    void tokenRefreshUpdatesSameRow() {
        // given
        register(userId, "device-1", "token-1");

        // when
        register(userId, "device-1", "token-2");

        // then
        assertThat(userDeviceStore.size()).isEqualTo(1);
        assertThat(userDeviceStore.find("device-1").orElseThrow().pushToken()).isEqualTo("token-2");
    }

    @Test
    @DisplayName("둘째 기기를 등록하면 첫 기기는 꺼지고 마지막 기기 하나만 켜진다")
    void secondDeviceDeactivatesFirst() {
        // given
        register(userId, "device-1", "token-1");

        // when -> 로그인 세션이 계정당 하나라 이전 기기는 이미 로그아웃된 상태다
        register(userId, "device-2", "token-2");

        // then
        assertThat(userDeviceStore.activeDeviceIds(userId)).containsExactly("device-2");
        assertThat(userDeviceStore.find("device-1").orElseThrow().active()).isFalse();
    }

    @Test
    @DisplayName("다시 쓰는 기기를 등록하면 그 기기가 다시 켜진다")
    void reRegisteringOldDeviceReactivatesIt() {
        // given
        register(userId, "device-1", "token-1");
        register(userId, "device-2", "token-2");

        // when
        register(userId, "device-1", "token-1");

        // then
        assertThat(userDeviceStore.activeDeviceIds(userId)).containsExactly("device-1");
    }

    @Test
    @DisplayName("다른 계정으로 같은 기기를 등록하면 행 주인이 바뀌고 이전 계정에는 켜진 기기가 없다")
    void accountSwitchChangesOwner() {
        // given
        register(userId, "device-1", "token-1");

        // when -> 한 기기에서 로그아웃 없이 다른 계정으로 로그인했다
        register(otherUserId, "device-1", "token-1");

        // then
        assertThat(userDeviceStore.size()).isEqualTo(1);
        assertThat(userDeviceStore.find("device-1").orElseThrow().userId()).isEqualTo(otherUserId);
        assertThat(userDeviceStore.activeDeviceIds(userId)).isEmpty();
        assertThat(userDeviceStore.activeDeviceIds(otherUserId)).containsExactly("device-1");
    }

    @Test
    @DisplayName("이미 탈퇴한 유저의 등록은 아무것도 저장하지 않는다")
    void deletedUserIsSkipped() {
        // when -> 잠금을 기다리는 사이 탈퇴가 끝난 경우다
        register(UuidCreator.getTimeOrderedEpoch(), "device-1", "token-1");

        // then
        assertThat(userDeviceStore.size()).isZero();
    }

    @Test
    @DisplayName("한 유저의 등록이 다른 유저의 기기를 끄지 않는다")
    void doesNotAffectOtherUsers() {
        // given
        register(otherUserId, "device-other", "token-other");

        // when
        register(userId, "device-1", "token-1");

        // then
        assertThat(userDeviceStore.activeDeviceIds(otherUserId)).containsExactly("device-other");
    }
}
