package com.runiverse.running_service.domain.running.room.exception;

import com.runiverse.running_service.domain.common.exception.BusinessException;
import com.runiverse.running_service.domain.common.exception.RunningRoomErrorCode;

// 초대 방이 아니거나 모집 중이 아니다 — 시각 기준 마감은 운영값이라 애플리케이션이 본다
public class RoomNotInvitableException extends BusinessException {

    public RoomNotInvitableException() {
        super(RunningRoomErrorCode.ROOM_NOT_INVITABLE);
    }
}
