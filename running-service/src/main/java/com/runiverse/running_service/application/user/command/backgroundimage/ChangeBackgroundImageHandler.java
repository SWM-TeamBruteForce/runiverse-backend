package com.runiverse.running_service.application.user.command.backgroundimage;

import com.runiverse.running_service.application.user.common.UploadedImageVerifier;
import com.runiverse.running_service.application.user.common.UserImageType;
import com.runiverse.running_service.application.user.port.in.ChangeBackgroundImageUsecase;
import com.runiverse.running_service.application.user.port.out.UpdateBackgroundImagePort;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.user.vo.BackgroundImageKey;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class ChangeBackgroundImageHandler implements ChangeBackgroundImageUsecase {

    private final UploadedImageVerifier uploadedImageVerifier;
    private final UpdateBackgroundImagePort updateBackgroundImagePort;

    @Override
    public ChangeBackgroundImageResult handle(ChangeBackgroundImageCommand command) {
        // 1. 본인 key인지, 실제로 올라갔는지, 크기·형식이 맞는지 확인 - 클라가 보낸 key를 믿지 않음
        String key = command.backgroundImageKey();
        uploadedImageVerifier.verify(UserImageType.BACKGROUND, command.userId(), key);

        // 2. user에 background_image_key 업데이트
        updateBackgroundImagePort.updateBackgroundImage(
                new UserId(command.userId()), new BackgroundImageKey(key));
        return new ChangeBackgroundImageResult(command.userId(), key);
    }
}
