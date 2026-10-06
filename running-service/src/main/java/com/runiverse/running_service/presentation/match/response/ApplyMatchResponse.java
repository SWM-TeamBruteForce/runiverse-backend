package com.runiverse.running_service.presentation.match.response;

import com.runiverse.running_service.application.match.command.apply.ApplyMatchResult;

public record ApplyMatchResponse(Long runningRoomId) {

    public static ApplyMatchResponse from(ApplyMatchResult result) {
        return new ApplyMatchResponse(result.runningRoomId());
    }
}
