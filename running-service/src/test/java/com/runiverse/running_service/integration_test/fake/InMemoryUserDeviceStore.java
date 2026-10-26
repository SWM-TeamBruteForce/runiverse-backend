package com.runiverse.running_service.integration_test.fake;

import com.runiverse.running_service.application.device.port.out.DeactivateUserDevicesPort;
import com.runiverse.running_service.application.device.port.out.DeleteUserDevicesPort;
import com.runiverse.running_service.application.device.port.out.UpsertUserDevicePort;
import com.runiverse.running_service.domain.common.vo.Platform;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.device.UserDevice;
import com.runiverse.running_service.domain.device.vo.DeviceId;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

// UserDevicePersistenceAdapter를 대신한다 — 기기 ID 하나에 행 하나다
public class InMemoryUserDeviceStore implements UpsertUserDevicePort, DeactivateUserDevicesPort,
        DeleteUserDevicesPort {

    private final Map<String, StoredDevice> devices = new HashMap<>();

    @Override
    public void upsert(UserDevice userDevice) {
        devices.put(userDevice.getDeviceId().value(), new StoredDevice(
                userDevice.getUserId().value(),
                userDevice.getPushToken().value(),
                userDevice.getPlatform(),
                userDevice.getAppVersion().value(),
                true));
    }

    @Override
    public void deactivateOthers(UserId userId, DeviceId deviceId) {
        devices.replaceAll((id, device) -> device.userId().equals(userId.value()) && !id.equals(deviceId.value())
                ? device.deactivated()
                : device);
    }

    @Override
    public void deactivateAll(UserId userId) {
        devices.replaceAll((id, device) -> device.userId().equals(userId.value()) ? device.deactivated() : device);
    }

    @Override
    public void deleteAll(UserId userId) {
        devices.values().removeIf(device -> device.userId().equals(userId.value()));
    }

    // 검증 전용
    public Optional<StoredDevice> find(String deviceId) {
        return Optional.ofNullable(devices.get(deviceId));
    }

    public List<String> activeDeviceIds(UUID userId) {
        return devices.entrySet().stream()
                .filter(entry -> entry.getValue().userId().equals(userId) && entry.getValue().active())
                .map(Map.Entry::getKey)
                .toList();
    }

    public int size() {
        return devices.size();
    }

    public record StoredDevice(UUID userId, String pushToken, Platform platform, String appVersion, boolean active) {

        private StoredDevice deactivated() {
            return new StoredDevice(userId, pushToken, platform, appVersion, false);
        }
    }
}
