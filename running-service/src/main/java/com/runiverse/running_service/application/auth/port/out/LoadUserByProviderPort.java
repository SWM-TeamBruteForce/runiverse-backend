package com.runiverse.running_service.application.auth.port.out;

import com.runiverse.running_service.domain.user.User;
import com.runiverse.running_service.domain.user.vo.Provider;

import java.util.Optional;

public interface LoadUserByProviderPort {

    Optional<User> loadByProvider(Provider provider, String providerId);
}
