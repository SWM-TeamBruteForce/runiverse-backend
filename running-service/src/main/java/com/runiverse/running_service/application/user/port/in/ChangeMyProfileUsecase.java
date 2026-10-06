package com.runiverse.running_service.application.user.port.in;

import com.runiverse.running_service.application.user.command.profile.ChangeMyProfileCommand;
import com.runiverse.running_service.application.user.command.profile.ChangeMyProfileResult;

public interface ChangeMyProfileUsecase {

    ChangeMyProfileResult handle(ChangeMyProfileCommand command);
}
