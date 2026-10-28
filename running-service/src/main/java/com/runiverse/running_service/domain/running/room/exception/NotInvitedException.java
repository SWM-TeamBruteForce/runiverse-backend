package com.runiverse.running_service.domain.running.room.exception;

import com.runiverse.running_service.domain.common.exception.BusinessException;
import com.runiverse.running_service.domain.common.exception.RunningRoomErrorCode;

// 이 방에 받는 사람의 초대 행이 하나도 없다 — 애플리케이션은 404로 미리 걸러 낸다
public class NotInvitedException extends BusinessException {

    public NotInvitedException() {
        super(RunningRoomErrorCode.NOT_INVITED);
    }
}
