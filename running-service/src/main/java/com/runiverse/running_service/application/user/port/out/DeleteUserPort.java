package com.runiverse.running_service.application.user.port.out;

import com.runiverse.running_service.domain.common.vo.UserId;

public interface DeleteUserPort {

    void deleteUser(UserId userId);
}
