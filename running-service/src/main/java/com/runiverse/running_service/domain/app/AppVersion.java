package com.runiverse.running_service.domain.app;

import com.runiverse.running_service.domain.common.exception.PlatformRequiredException;
import com.runiverse.running_service.domain.common.vo.Platform;
import com.runiverse.running_service.domain.common.vo.VersionName;
import lombok.Builder;
import lombok.Getter;

// 플랫폼마다 몇 버전부터 쓸 수 있고 최신은 몇 버전인가 — 운영이 SQL로 바꾸는 값이라 앱은 읽기만 한다.
// 최소 ≤ 최신은 DB가 막는다
@Getter
public class AppVersion {

    private final Platform platform;
    private final VersionName minimumVersion;
    private final VersionName latestVersion;

    @Builder
    private AppVersion(Platform platform, String minimumVersion, String latestVersion) {
        if (platform == null) {
            throw new PlatformRequiredException();
        }
        this.platform = platform;
        this.minimumVersion = new VersionName(minimumVersion);
        this.latestVersion = new VersionName(latestVersion);
    }

    public boolean requiresUpdate(VersionName versionName) {
        return versionName.isLowerThan(minimumVersion);
    }
}
