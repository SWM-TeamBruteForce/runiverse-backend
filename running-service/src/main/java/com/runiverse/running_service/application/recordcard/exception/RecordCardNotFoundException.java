package com.runiverse.running_service.application.recordcard.exception;

import com.runiverse.running_service.application.common.exception.BusinessException;
import com.runiverse.running_service.application.common.exception.ResourceErrorCode;

// 볼 카드가 없다 — 없거나, 지워졌거나, 작성자가 탈퇴했거나, 볼 수 없는 카드다. 넷을 가르지 않는다.
// 볼 수 없는 카드를 403으로 나누면 남의 비공개 카드가 있다는 사실이 드러난다
public class RecordCardNotFoundException extends BusinessException {

    public RecordCardNotFoundException() {
        super(ResourceErrorCode.NOT_FOUND);
    }
}
