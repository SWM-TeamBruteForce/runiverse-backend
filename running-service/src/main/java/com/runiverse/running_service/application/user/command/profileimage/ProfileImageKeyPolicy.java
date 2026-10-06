package com.runiverse.running_service.application.user.command.profileimage;

import com.runiverse.running_service.domain.user.vo.ProfileImageKey;

import java.util.UUID;

public final class ProfileImageKeyPolicy {

    private static final String KEY_PREFIX = "profiles";

    private ProfileImageKeyPolicy() {
    }

    public static ProfileImageKey create(UUID userId, UUID imageId, ProfileImageContentType contentType) {
        return new ProfileImageKey("%s%s.%s".formatted(prefixOf(userId), imageId, contentType.getExtension()));
    }

    public static boolean isOwnedBy(String key, UUID userId) {
        return key.startsWith(prefixOf(userId));
    }

    public static String prefixOf(UUID userId) {
        return "%s/%s/".formatted(KEY_PREFIX, userId);
    }
}
