package com.runiverse.running_service.domain.recordcard.vo;

import com.runiverse.running_service.domain.recordcard.exception.RecordCardImageKeyRequiredException;
import com.runiverse.running_service.domain.recordcard.exception.RecordCardImageKeyTooLongException;

// 카드 사진의 S3 key — 본인 프리픽스인지·업로드됐는지는 저장소를 봐야 해서 여기서 검사하지 않는다
public record RecordCardImageKey(String value) {

    private static final int MAX_LENGTH = 255;

    public RecordCardImageKey {
        if (value == null) {
            throw new RecordCardImageKeyRequiredException();
        }
        value = value.trim();
        if (value.isEmpty()) {
            throw new RecordCardImageKeyRequiredException();
        }
        if (value.length() > MAX_LENGTH) {
            throw new RecordCardImageKeyTooLongException();
        }
    }
}
