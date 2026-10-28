package com.runiverse.running_service.domain.running.room;

import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.running.room.vo.RoomInvitationStatus;

public record InvitationDraft(UserId inviterId, UserId inviteeId,
                              RoomInvitationStatus status, int inviteCount) {

}
