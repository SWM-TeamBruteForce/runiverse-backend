package com.runiverse.running_service.domain.device;

import com.runiverse.running_service.domain.common.exception.PlatformRequiredException;
import com.runiverse.running_service.domain.common.vo.Platform;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.common.vo.VersionName;
import com.runiverse.running_service.domain.device.vo.DeviceId;
import com.runiverse.running_service.domain.device.vo.PushToken;
import lombok.Getter;

import java.util.UUID;

// 푸시를 받을 기기 — 지금 이 기기로 로그인한 유저(userId)에게 묶인다. 유저당 마지막에 등록한 기기만 받는다
@Getter
public class UserDevice {

    private final UserId userId;
    private final DeviceId deviceId;
    private final PushToken pushToken;
    private final Platform platform;
    private final VersionName appVersion;   // 등록할 때의 앱 버전

    private UserDevice(UUID userId, String deviceId, String pushToken, Platform platform, String appVersion) {
        if (platform == null) {
            throw new PlatformRequiredException();
        }
        this.userId = new UserId(userId);
        this.deviceId = new DeviceId(deviceId);
        this.pushToken = new PushToken(pushToken);
        this.platform = platform;
        this.appVersion = new VersionName(appVersion);
    }

    public static UserDevice register(UUID userId, String deviceId, String pushToken,
                                      Platform platform, String appVersion) {
        return new UserDevice(userId, deviceId, pushToken, platform, appVersion);
    }
}
