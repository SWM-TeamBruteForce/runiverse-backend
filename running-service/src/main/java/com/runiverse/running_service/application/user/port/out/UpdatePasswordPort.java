package com.runiverse.running_service.application.user.port.out;

import com.runiverse.running_service.domain.user.vo.PasswordHash;
import com.runiverse.running_service.domain.common.vo.UserId;

public interface UpdatePasswordPort {

    void updatePassword(UserId userId, PasswordHash passwordHash);
}
