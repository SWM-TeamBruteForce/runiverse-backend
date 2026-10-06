package com.runiverse.running_service.application.running.port.in;

import com.runiverse.running_service.application.running.command.start.StartRunningRoomCommand;

public interface StartRunningRoomUsecase {

    // 시작 시각 정각의 방 전이 — 예약이 깨워서 부른다. 요청으로는 들어오지 않는다
    void handle(StartRunningRoomCommand command);
}
