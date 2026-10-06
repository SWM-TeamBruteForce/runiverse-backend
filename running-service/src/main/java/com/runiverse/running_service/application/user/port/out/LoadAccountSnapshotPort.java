package com.runiverse.running_service.application.user.port.out;

import com.runiverse.running_service.domain.common.vo.UserId;

import java.util.Optional;

public interface LoadAccountSnapshotPort {

    Optional<AccountSnapshot> loadAccountSnapshot(UserId userId);
}
