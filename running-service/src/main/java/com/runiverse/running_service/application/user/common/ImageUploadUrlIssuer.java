package com.runiverse.running_service.application.user.common;

import com.runiverse.running_service.application.user.port.out.GenerateImageIdPort;
import com.runiverse.running_service.application.user.port.out.GenerateUploadUrlPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

// 프로필·배경 사진의 업로드 URL 발급 — 종류는 key prefix만 가른다
@Component
@RequiredArgsConstructor
public class ImageUploadUrlIssuer {

    private final GenerateImageIdPort generateImageIdPort;
    private final GenerateUploadUrlPort generateUploadUrlPort;

    public IssuedUpload issue(UserImageType type, UUID userId, String mimeType, long fileSizeBytes) {
        // 0. 허용된 형식인지 확인하고 확장자를 얻음
        ImageContentType contentType = ImageContentType.from(mimeType);

        // 1. 이미지 저장을 위한 키네임 생성
        // prefix에 userId를 넣어 소유자 검증
        String key = type.createKey(userId, generateImageIdPort.generate(), contentType);

        // 2. 키 위치에 맞는 upload_url 생성
        // contentType이 서명에 포함되므로 업로드 헤더와 일치 해야함
        String uploadUrl = generateUploadUrlPort.generate(key, contentType.getMimeType(), fileSizeBytes);

        return new IssuedUpload(key, uploadUrl);
    }

    public record IssuedUpload(String key, String uploadUrl) {

    }
}
