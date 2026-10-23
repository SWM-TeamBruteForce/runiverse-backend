package com.runiverse.running_service.application.user.common;

import com.runiverse.running_service.application.common.exception.BusinessException;
import com.runiverse.running_service.application.user.exception.BackgroundImageNotUploadedException;
import com.runiverse.running_service.application.user.exception.InvalidBackgroundImageException;
import com.runiverse.running_service.application.user.exception.InvalidProfileImageException;
import com.runiverse.running_service.application.user.exception.ProfileImageNotUploadedException;
import com.runiverse.running_service.domain.user.User;
import com.runiverse.running_service.domain.user.vo.BackgroundImageKey;
import com.runiverse.running_service.domain.user.vo.ProfileImageKey;
import lombok.RequiredArgsConstructor;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;

// 프로필·배경 사진이 다른 점은 여기에만 둔다 — 공유 컴포넌트는 종류로 분기하지 않는다
@RequiredArgsConstructor
public enum UserImageType {

    PROFILE(
            "profiles",
            InvalidProfileImageException::new,
            ProfileImageNotUploadedException::new,
            user -> user.getProfileImageKey().map(ProfileImageKey::value)
    ),
    BACKGROUND(
            "backgrounds",
            InvalidBackgroundImageException::new,
            BackgroundImageNotUploadedException::new,
            user -> user.getBackgroundImageKey().map(BackgroundImageKey::value)
    );

    private final String keyPrefix;
    private final Supplier<BusinessException> invalid;
    private final Supplier<BusinessException> notUploaded;
    private final Function<User, Optional<String>> keyReader;

    // 끝의 /까지 포함해야 userId 뒤에 다른 문자가 붙은 key를 막고, 탈퇴 시 지우는 범위도 유저 폴더로 한정된다
    public String prefixOf(UUID userId) {
        return "%s/%s/".formatted(keyPrefix, userId);
    }

    public String createKey(UUID userId, UUID imageId, ImageContentType contentType) {
        return "%s%s.%s".formatted(prefixOf(userId), imageId, contentType.getExtension());
    }

    // prefix가 종류마다 달라 같은 유저라도 프로필 key를 배경으로 반영할 수 없다
    public boolean isOwnedBy(String key, UUID userId) {
        return key.startsWith(prefixOf(userId));
    }

    public Optional<String> keyOf(User user) {
        return keyReader.apply(user);
    }

    public BusinessException invalid() {
        return invalid.get();
    }

    public BusinessException notUploaded() {
        return notUploaded.get();
    }
}
