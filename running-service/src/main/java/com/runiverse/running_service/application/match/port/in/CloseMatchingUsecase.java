package com.runiverse.running_service.application.match.port.in;

import com.runiverse.running_service.application.match.command.close.CloseMatchingCommand;

public interface CloseMatchingUsecase {

    // 모집 마감 확정 — 예약이 깨워서 부른다. 요청으로는 들어오지 않는다
    void handle(CloseMatchingCommand command);
}
