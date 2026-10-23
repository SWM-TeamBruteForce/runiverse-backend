package com.runiverse.running_service.application.match.exception;

import com.runiverse.running_service.application.common.exception.BusinessException;
import com.runiverse.running_service.application.common.exception.MatchErrorCode;

// 문구는 MatchSlotPolicy가 운영값으로 만든다 — 설정이 바뀌면 안내 문구도 함께 바뀌어야 한다
public class InvalidMatchOptionException extends BusinessException {

    public InvalidMatchOptionException(String message) {
        super(MatchErrorCode.INVALID_MATCH_OPTION, message);
    }
}
