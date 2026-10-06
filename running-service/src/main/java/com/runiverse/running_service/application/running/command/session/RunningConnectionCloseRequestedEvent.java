package com.runiverse.running_service.application.running.command.session;

import com.runiverse.running_service.domain.common.vo.UserId;

public record RunningConnectionCloseRequestedEvent(UserId userId) {

}
