package com.runiverse.running_service.application.match.exception;

import com.runiverse.running_service.application.common.exception.BusinessException;
import com.runiverse.running_service.application.common.exception.InviteErrorCode;

// 친구가 이미 이 방의 참가자다 — 다른 방의 매칭 중보다 먼저 가려 정확한 이유를 준다
public class InviteeAlreadyInRoomException extends BusinessException {

    public InviteeAlreadyInRoomException() {
        super(InviteErrorCode.INVITEE_ALREADY_IN_ROOM);
    }
}
