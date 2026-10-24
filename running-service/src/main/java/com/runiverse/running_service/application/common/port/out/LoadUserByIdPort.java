package com.runiverse.running_service.application.common.port.out;

import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.user.User;

import java.util.Optional;

public interface LoadUserByIdPort {

    Optional<User> loadById(UserId userId);
}
