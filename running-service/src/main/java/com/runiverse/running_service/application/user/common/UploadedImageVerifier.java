package com.runiverse.running_service.application.user.common;

import com.runiverse.running_service.application.user.port.out.LoadUploadedImagePort;
import com.runiverse.running_service.application.user.port.out.UploadedImage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

// 반영 전에 클라가 보낸 key를 검증한다 — 실패 사유는 같고 응답 코드만 종류별로 갈린다
@Component
@RequiredArgsConstructor
public class UploadedImageVerifier {

    private static final long MAX_SIZE_BYTES = 10L * 1024 * 1024;
    private final LoadUploadedImagePort loadUploadedImagePort;

    public void verify(UserImageType type, UUID userId, String key) {
        // 1. 유저가 보낸 티켓 검증 - 남의 key, 다른 종류의 key를 막는다
        if (!type.isOwnedBy(key, userId)) {
            throw type.invalid();
        }

        // 2. 실제로 올라갔는지 확인한다 - 클라가 보낸 key를 믿지 않음
        UploadedImage uploaded = loadUploadedImagePort.load(key)
                .orElseThrow(type::notUploaded);

        // 3. 저장된 실물의 크기와 형식을 확인한다.
        if (uploaded.sizeBytes() > MAX_SIZE_BYTES) {
            throw type.invalid();
        }
        requireSupported(type, uploaded.contentType());
    }

    private void requireSupported(UserImageType type, String contentType) {
        try {
            ImageContentType.from(contentType);
        } catch (IllegalArgumentException e) {
            throw type.invalid();
        }
    }
}
