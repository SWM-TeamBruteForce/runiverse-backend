package com.runiverse.running_service.application.user.command.profileimage;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;

@Getter
@RequiredArgsConstructor
public enum ProfileImageContentType {
    JPEG("image/jpeg", "jpg"),
    PNG("image/png", "png"),
    WEBP("image/webp", "webp");
    private final String mimeType;
    private final String extension;

    public static ProfileImageContentType from(String mimeType) {
        return Arrays.stream(values())
                .filter(contentType -> contentType.mimeType.equalsIgnoreCase(mimeType))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("지원하지 않는 이미지 형식: " + mimeType));
    }
}
