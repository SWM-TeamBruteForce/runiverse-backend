package com.runiverse.running_service.application.app.port.out;

import com.runiverse.running_service.domain.app.AppVersion;
import com.runiverse.running_service.domain.common.vo.Platform;

import java.util.Optional;

public interface LoadAppVersionPort {

    Optional<AppVersion> load(Platform platform);
}
