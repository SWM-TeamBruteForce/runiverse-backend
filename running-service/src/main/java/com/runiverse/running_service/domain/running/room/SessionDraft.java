package com.runiverse.running_service.domain.running.room;

import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.running.player.vo.RunningPlayerId;

public record SessionDraft(UserId userId, RunningPlayerId runningPlayerId, int leaveCount, boolean connected) {

}
