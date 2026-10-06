package com.runiverse.running_service.application.auth.port.out;

import com.runiverse.running_service.domain.user.User;

import java.util.Optional;

public interface LoadUserByEmailPort {

    Optional<User> loadByEmail(String email);
}
