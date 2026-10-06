package com.runiverse.running_service.application.match.port.out;

import com.runiverse.running_service.domain.common.vo.UserId;

public interface ExistsActiveApplicationPort {

    // deleted_at IS NULL인 신청이 있는가 — 러닝 중(RUNNING)도 여기 걸린다.
    // 매칭 화면 상태를 가르는 판정(status='JOINED')보다 넓다
    boolean existsActive(UserId userId);
}
