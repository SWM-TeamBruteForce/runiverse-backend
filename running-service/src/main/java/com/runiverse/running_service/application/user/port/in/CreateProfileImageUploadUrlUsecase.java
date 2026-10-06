package com.runiverse.running_service.application.user.port.in;

import com.runiverse.running_service.application.user.command.profileimage.CreateProfileImageUploadUrlCommand;
import com.runiverse.running_service.application.user.command.profileimage.CreateProfileImageUploadUrlResult;

public interface CreateProfileImageUploadUrlUsecase {

    CreateProfileImageUploadUrlResult handle(CreateProfileImageUploadUrlCommand command);
}
