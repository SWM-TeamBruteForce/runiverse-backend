package com.runiverse.running_service.application.recordcard.exception;

import com.runiverse.running_service.application.common.exception.BusinessException;
import com.runiverse.running_service.application.common.exception.RecordCardErrorCode;

// 문구의 크기는 운영값(record-card.draft.max-thumbnail-size)으로 만든다 — 설정이 바뀌면 안내 문구도 함께 바뀐다
public class RecordCardThumbnailTooLargeException extends BusinessException {

    public RecordCardThumbnailTooLargeException(long maxThumbnailSizeMegabytes) {
        super(RecordCardErrorCode.RECORD_CARD_THUMBNAIL_TOO_LARGE,
                "썸네일은 %dMB 이하만 업로드할 수 있습니다.".formatted(maxThumbnailSizeMegabytes));
    }
}
