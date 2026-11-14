package com.runiverse.running_service.domain.recordcard.exception;

import com.runiverse.running_service.domain.common.exception.BusinessException;
import com.runiverse.running_service.domain.common.exception.RecordCardErrorCode;

// 음악 key는 COMPLETED인 음악에만 있다 — 생성 중·실패한 음악에 key가 있거나, COMPLETED인데 key가 없으면 저장값이 깨진 것이다
public class MusicKeyStatusMismatchException extends BusinessException {

    public MusicKeyStatusMismatchException() {
        super(RecordCardErrorCode.MUSIC_KEY_STATUS_MISMATCH);
    }
}
