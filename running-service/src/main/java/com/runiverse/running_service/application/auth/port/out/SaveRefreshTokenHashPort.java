package com.runiverse.running_service.application.auth.port.out;

import com.runiverse.running_service.domain.common.vo.UserId;

public interface SaveRefreshTokenHashPort {

    void save(UserId userId, String hashedRefreshToken);
}
