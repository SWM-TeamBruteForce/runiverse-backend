package com.runiverse.running_service.application.match.port.out;

import com.runiverse.running_service.domain.running.player.RunningPlayer;

public interface CreateMatchApplicationPort {

    RunningPlayer create(RunningPlayer player);
}
