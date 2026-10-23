package com.runiverse.running_service.application.app.query.version;

import com.runiverse.running_service.application.app.port.in.CheckAppVersionUsecase;
import com.runiverse.running_service.application.app.port.out.LoadAppVersionPort;
import com.runiverse.running_service.domain.app.AppVersion;
import com.runiverse.running_service.domain.app.vo.VersionName;
import com.runiverse.running_service.domain.common.vo.Platform;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

// 모든 요청 앞에서 버전 검사 필터가 부른다 — 쿼리 하나라 트랜잭션을 열지 않는다.
// 열면 요청마다 BEGIN·COMMIT 왕복이 붙는다
@Slf4j
@Service
@RequiredArgsConstructor
public class CheckAppVersionHandler implements CheckAppVersionUsecase {

    private final LoadAppVersionPort loadAppVersionPort;

    @Override
    public CheckAppVersionResult handle(CheckAppVersionQuery query) {
        Platform platform = Platform.from(query.platform());
        VersionName versionName = new VersionName(query.versionName());

        // 캐시 없이 매번 읽는다 — 운영이 최소 버전을 올리면 다음 요청부터 바로 걸린다
        AppVersion appVersion = loadAppVersionPort.load(platform)
                .orElseThrow(() -> new IllegalStateException("앱 버전 행이 없다 — platform=" + platform));
        boolean updateRequired = appVersion.requiresUpdate(versionName);
        if (updateRequired) {
            log.info("[앱] 버전 확인 실패: 최소 버전 미만 - platform={}, versionName={}, minimumVersion={}",
                    platform, versionName.value(), appVersion.getMinimumVersion().value());
        }
        return new CheckAppVersionResult(updateRequired);
    }
}
