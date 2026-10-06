package com.runiverse.running_service.application.user.port.out;

import com.runiverse.running_service.domain.common.vo.UserId;

public interface RedactDeletedUserPort {

    // 행은 남기고 신원을 특정하는 두 값만 비운다 — 나머지는 통계로 계속 쓴다
    void redact(UserId userId);
}
