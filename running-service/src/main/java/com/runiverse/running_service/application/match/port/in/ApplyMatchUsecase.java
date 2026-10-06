package com.runiverse.running_service.application.match.port.in;

import com.runiverse.running_service.application.match.command.apply.ApplyMatchCommand;
import com.runiverse.running_service.application.match.command.apply.ApplyMatchResult;

public interface ApplyMatchUsecase {

    ApplyMatchResult handle(ApplyMatchCommand command);
}
