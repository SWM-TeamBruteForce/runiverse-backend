package com.runiverse.running_service.application.user.command.profileimage;

import com.runiverse.running_service.application.user.common.ImageUploadUrlIssuer;
import com.runiverse.running_service.application.user.common.ImageUploadUrlIssuer.IssuedUpload;
import com.runiverse.running_service.application.user.common.UserImageType;
import com.runiverse.running_service.application.user.port.in.CreateProfileImageUploadUrlUsecase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CreateProfileImageUploadUrlHandler implements CreateProfileImageUploadUrlUsecase {

    private final ImageUploadUrlIssuer imageUploadUrlIssuer;

    @Override
    public CreateProfileImageUploadUrlResult handle(CreateProfileImageUploadUrlCommand command) {
        IssuedUpload issued = imageUploadUrlIssuer.issue(
                UserImageType.PROFILE, command.userId(), command.mimeType(), command.fileSizeBytes());
        return new CreateProfileImageUploadUrlResult(issued.key(), issued.uploadUrl());
    }
}
