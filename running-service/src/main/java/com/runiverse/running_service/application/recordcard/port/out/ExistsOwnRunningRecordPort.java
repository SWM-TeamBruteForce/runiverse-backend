package com.runiverse.running_service.application.recordcard.port.out;

import com.runiverse.running_service.domain.common.vo.RunningRecordId;
import com.runiverse.running_service.domain.common.vo.UserId;

public interface ExistsOwnRunningRecordPort {

    // 카드를 붙일 수 있는 기록인지 — 있고, 이 사람 것이고, 숨긴 방의 기록이 아니어야 한다.
    // 숨긴 방의 기록은 기록 목록(7-1)에서 빠지므로 없는 기록으로 본다. 셋 중 무엇이 틀렸는지는 가르지 않는다(모두 404)
    boolean existsOwnedBy(RunningRecordId runningRecordId, UserId userId);
}
