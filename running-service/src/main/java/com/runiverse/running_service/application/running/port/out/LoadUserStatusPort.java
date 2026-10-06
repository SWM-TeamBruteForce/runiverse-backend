package com.runiverse.running_service.application.running.port.out;

import com.runiverse.running_service.domain.common.vo.UserId;

import java.util.Optional;

public interface LoadUserStatusPort {

    // 활성 신청(deleted_at IS NULL)과 배정된 방을 한 번에 읽는다.
    // 신청이 없으면 비어 있고, 그것이 곧 IDLE이다
    Optional<UserStatusRow> loadStatus(UserId userId);
}
