package com.runiverse.running_service.application.user.command.backgroundimage;

import com.runiverse.running_service.application.user.common.ImageUploadUrlIssuer;
import com.runiverse.running_service.application.user.common.ImageUploadUrlIssuer.IssuedUpload;
import com.runiverse.running_service.application.user.common.UserImageType;
import com.runiverse.running_service.application.user.port.in.CreateBackgroundImageUploadUrlUsecase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CreateBackgroundImageUploadUrlHandler implements CreateBackgroundImageUploadUrlUsecase {

    private final ImageUploadUrlIssuer imageUploadUrlIssuer;

    @Override
    public CreateBackgroundImageUploadUrlResult handle(CreateBackgroundImageUploadUrlCommand command) {
        IssuedUpload issued = imageUploadUrlIssuer.issue(
                UserImageType.BACKGROUND, command.userId(), command.mimeType(), command.fileSizeBytes());
        return new CreateBackgroundImageUploadUrlResult(issued.key(), issued.uploadUrl());
    }
}
