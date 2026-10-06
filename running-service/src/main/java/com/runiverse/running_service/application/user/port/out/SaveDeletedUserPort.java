package com.runiverse.running_service.application.user.port.out;

import com.runiverse.running_service.domain.user.DeletedUser;

public interface SaveDeletedUserPort {

    void saveDeletedUser(DeletedUser deletedUser);
}
