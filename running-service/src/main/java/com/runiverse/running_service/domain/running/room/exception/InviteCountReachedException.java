package com.runiverse.running_service.domain.running.room.exception;

import com.runiverse.running_service.domain.common.exception.BusinessException;
import com.runiverse.running_service.domain.common.exception.RunningRoomErrorCode;

// 보낸 사람이 이 받는 사람에게 상한만큼 보냈다 — 애플리케이션이 409로 먼저 거르고, 이건 그 뒤의 방어선이다
public class InviteCountReachedException extends BusinessException {

    public InviteCountReachedException() {
        super(RunningRoomErrorCode.INVITE_COUNT_REACHED);
    }
}
