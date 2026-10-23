package com.runiverse.running_service.application.user.port.in;

import com.runiverse.running_service.application.user.command.backgroundimage.DeleteBackgroundImageCommand;

public interface DeleteBackgroundImageUsecase {

    void handle(DeleteBackgroundImageCommand command);
}
