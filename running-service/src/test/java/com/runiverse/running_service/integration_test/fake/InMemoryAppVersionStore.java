package com.runiverse.running_service.integration_test.fake;

import com.runiverse.running_service.application.app.port.out.LoadAppVersionPort;
import com.runiverse.running_service.domain.app.AppVersion;
import com.runiverse.running_service.domain.common.vo.Platform;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

// AppVersionPersistenceAdapter를 대신한다 — 운영이 SQL로 행을 넣고 바꾸는 것을 register로 흉내 낸다
public class InMemoryAppVersionStore implements LoadAppVersionPort {

    private final Map<Platform, AppVersion> appVersions = new EnumMap<>(Platform.class);

    public void register(Platform platform, String minimumVersion, String latestVersion) {
        appVersions.put(platform, AppVersion.builder()
                .platform(platform)
                .minimumVersion(minimumVersion)
                .latestVersion(latestVersion)
                .build());
    }

    @Override
    public Optional<AppVersion> load(Platform platform) {
        return Optional.ofNullable(appVersions.get(platform));
    }
}
