package com.runiverse.running_service.application.user.port.in;

import com.runiverse.running_service.application.user.command.settings.ChangeMySettingsCommand;
import com.runiverse.running_service.application.user.command.settings.ChangeMySettingsResult;

public interface ChangeMySettingsUsecase {

    ChangeMySettingsResult handle(ChangeMySettingsCommand command);
}
