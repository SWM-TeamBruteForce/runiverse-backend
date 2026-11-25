package com.runiverse.running_service.application.recordcard.exception;

import com.runiverse.running_service.application.common.exception.BusinessException;
import com.runiverse.running_service.application.common.exception.RecordCardErrorCode;

// 수정(9-3)에서 사진 목록 없이 보낸 썸네일 위치가 지금 사진 범위를 벗어났다(400) — 도메인에 넘기면 도메인 예외(500)가 된다
public class RecordCardThumbnailOutOfRangeException extends BusinessException {

    public RecordCardThumbnailOutOfRangeException() {
        super(RecordCardErrorCode.RECORD_CARD_THUMBNAIL_OUT_OF_RANGE);
    }
}
