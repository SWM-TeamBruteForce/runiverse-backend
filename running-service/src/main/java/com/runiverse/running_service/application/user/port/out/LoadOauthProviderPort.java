package com.runiverse.running_service.application.user.port.out;

import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.user.vo.Provider;

import java.util.Optional;

public interface LoadOauthProviderPort {

    Optional<Provider> loadProvider(UserId userId);
}
