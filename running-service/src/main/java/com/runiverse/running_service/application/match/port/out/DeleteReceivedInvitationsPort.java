package com.runiverse.running_service.application.match.port.out;

import com.runiverse.running_service.domain.common.vo.UserId;

public interface DeleteReceivedInvitationsPort {

    void deleteAll(UserId inviteeId);
}
