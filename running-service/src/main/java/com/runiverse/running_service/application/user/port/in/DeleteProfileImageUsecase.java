package com.runiverse.running_service.application.user.port.in;

import com.runiverse.running_service.application.user.command.profileimage.DeleteProfileImageCommand;

public interface DeleteProfileImageUsecase {

    void handle(DeleteProfileImageCommand command);
}
