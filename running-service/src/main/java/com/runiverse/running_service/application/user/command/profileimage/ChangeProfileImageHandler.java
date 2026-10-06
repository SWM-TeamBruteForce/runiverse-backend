package com.runiverse.running_service.application.user.command.profileimage;

import com.runiverse.running_service.application.user.exception.InvalidProfileImageException;
import com.runiverse.running_service.application.user.exception.ProfileImageNotUploadedException;
import com.runiverse.running_service.application.user.port.in.ChangeProfileImageUsecase;
import com.runiverse.running_service.application.user.port.out.LoadUploadedImagePort;
import com.runiverse.running_service.application.user.port.out.UpdateProfileImagePort;
import com.runiverse.running_service.application.user.port.out.UploadedImage;
import com.runiverse.running_service.domain.user.vo.ProfileImageKey;
import com.runiverse.running_service.domain.common.vo.UserId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class ChangeProfileImageHandler implements ChangeProfileImageUsecase {

    private static final long MAX_SIZE_BYTES = 10L * 1024 * 1024;
    private final LoadUploadedImagePort loadUploadedImagePort;
    private final UpdateProfileImagePort updateProfileImagePort;

    @Override
    public ChangeProfileImageResult handle(ChangeProfileImageCommand command) {
        // 1. 유저가 보낸 티켓 검증
        String key = command.profileImageKey();
        if (!ProfileImageKeyPolicy.isOwnedBy(key, command.userId())) {
            throw new InvalidProfileImageException();
        }

        // 2. 실제로 올라갔는지 확인한다 - 클라가 보낸 key를 믿지 않음
        UploadedImage uploaded = loadUploadedImagePort.load(key)
                .orElseThrow(ProfileImageNotUploadedException::new);

        // 3. 저장된 실물의 크기와 형식을 확인한다.
        if (uploaded.sizeBytes() > MAX_SIZE_BYTES) {
            throw new InvalidProfileImageException();
        }
        requireSupported(uploaded.contentType());

        // 4. user에 profile_image_key 업데이트
        updateProfileImagePort.updateProfileImage(
                new UserId(command.userId()), new ProfileImageKey(key));
        return new ChangeProfileImageResult(command.userId(), key);
    }

    private void requireSupported(String contentType) {
        try {
            ProfileImageContentType.from(contentType);
        } catch (IllegalArgumentException e) {
            throw new InvalidProfileImageException();
        }
    }
}
