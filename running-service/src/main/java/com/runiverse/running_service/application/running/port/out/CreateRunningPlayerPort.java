package com.runiverse.running_service.application.running.port.out;

import com.runiverse.running_service.domain.running.player.RunningPlayer;

public interface CreateRunningPlayerPort {

    RunningPlayer create(RunningPlayer player);
}
