package com.runiverse.running_service.application.recordcard.port.out;

import com.runiverse.running_service.domain.common.vo.RunningRecordId;
import com.runiverse.running_service.domain.common.vo.UserId;

public interface LockOwnRunningRecordPort {

    // 카드 쓰기(작성·수정·삭제)의 잠금 — 기록 행을 FOR UPDATE로 잠근다. 기록과 카드가 1:1이라 이 행 하나로 줄을 세운다.
    // 잠그는 쿼리에 "이 사람 것이고 숨긴 방이 아님"을 함께 걸어, 행이 없으면 잠그지 않고 false(모두 404)
    boolean lockOwnedBy(RunningRecordId runningRecordId, UserId userId);
}
