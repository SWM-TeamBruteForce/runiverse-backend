package com.runiverse.running_service.application.user.port.in;

import com.runiverse.running_service.application.user.command.profileimage.ChangeProfileImageCommand;
import com.runiverse.running_service.application.user.command.profileimage.ChangeProfileImageResult;

public interface ChangeProfileImageUsecase {

    ChangeProfileImageResult handle(ChangeProfileImageCommand command);
}
