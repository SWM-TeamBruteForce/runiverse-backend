package com.runiverse.running_service.application.user.port.in;

import com.runiverse.running_service.application.user.command.backgroundimage.CreateBackgroundImageUploadUrlCommand;
import com.runiverse.running_service.application.user.command.backgroundimage.CreateBackgroundImageUploadUrlResult;

public interface CreateBackgroundImageUploadUrlUsecase {

    CreateBackgroundImageUploadUrlResult handle(CreateBackgroundImageUploadUrlCommand command);
}
