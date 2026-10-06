package com.runiverse.running_service.application.match.exception;

import com.runiverse.running_service.application.common.exception.BusinessException;
import com.runiverse.running_service.application.common.exception.MatchErrorCode;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class MatchCooldownException extends BusinessException {

    private final LocalDateTime cooldownUntil;

    public MatchCooldownException(LocalDateTime cooldownUntil) {
        super(MatchErrorCode.MATCH_COOLDOWN);
        this.cooldownUntil = cooldownUntil;
    }
}
