package com.runiverse.running_service.domain.running.room.vo;

import com.runiverse.running_service.domain.running.room.exception.InvalidRunningRoomIdException;

public record RunningRoomId(Long value) {

    public RunningRoomId {
        if (value == null || value < 1) {
            throw new InvalidRunningRoomIdException();
        }
    }
}
