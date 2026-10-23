package com.runiverse.running_service.application.user.command.profileimage;

import com.runiverse.running_service.application.user.common.UploadedImageVerifier;
import com.runiverse.running_service.application.user.common.UserImageType;
import com.runiverse.running_service.application.user.port.in.ChangeProfileImageUsecase;
import com.runiverse.running_service.application.user.port.out.UpdateProfileImagePort;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.user.vo.ProfileImageKey;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class ChangeProfileImageHandler implements ChangeProfileImageUsecase {

    private final UploadedImageVerifier uploadedImageVerifier;
    private final UpdateProfileImagePort updateProfileImagePort;

    @Override
    public ChangeProfileImageResult handle(ChangeProfileImageCommand command) {
        // 1. 본인 key인지, 실제로 올라갔는지, 크기·형식이 맞는지 확인 - 클라가 보낸 key를 믿지 않음
        String key = command.profileImageKey();
        uploadedImageVerifier.verify(UserImageType.PROFILE, command.userId(), key);

        // 2. user에 profile_image_key 업데이트
        updateProfileImagePort.updateProfileImage(
                new UserId(command.userId()), new ProfileImageKey(key));
        return new ChangeProfileImageResult(command.userId(), key);
    }
}
