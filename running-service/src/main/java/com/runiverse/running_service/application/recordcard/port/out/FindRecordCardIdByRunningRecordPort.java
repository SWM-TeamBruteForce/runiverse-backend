package com.runiverse.running_service.application.recordcard.port.out;

import com.runiverse.running_service.domain.common.vo.RunningRecordId;
import com.runiverse.running_service.domain.recordcard.vo.RecordCardId;

import java.util.Optional;

public interface FindRecordCardIdByRunningRecordPort {

    // 이 기록에 붙은 카드의 ID — 기록과 카드는 1:1이라 많아야 하나다. 지운 카드는 행이 없다(하드 삭제).
    // 작성 요청(9-1)은 있으면 409로 거절하고, 확정(9-2)은 그 ID가 초안의 카드 ID와 같은지로
    // "응답을 못 받은 재시도"(같으면 그 카드를 돌려준다)와 "다른 초안이 먼저 만든 카드"(409)를 가른다
    Optional<RecordCardId> findIdByRunningRecord(RunningRecordId runningRecordId);
}
