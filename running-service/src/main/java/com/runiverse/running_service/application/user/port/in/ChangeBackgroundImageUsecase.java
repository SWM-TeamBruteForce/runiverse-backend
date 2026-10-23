package com.runiverse.running_service.application.user.port.in;

import com.runiverse.running_service.application.user.command.backgroundimage.ChangeBackgroundImageCommand;
import com.runiverse.running_service.application.user.command.backgroundimage.ChangeBackgroundImageResult;

public interface ChangeBackgroundImageUsecase {

    ChangeBackgroundImageResult handle(ChangeBackgroundImageCommand command);
}
