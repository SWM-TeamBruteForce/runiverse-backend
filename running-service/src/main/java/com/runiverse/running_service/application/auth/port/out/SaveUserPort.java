package com.runiverse.running_service.application.auth.port.out;

import com.runiverse.running_service.domain.user.User;

public interface SaveUserPort {

    User save(User user);
}
