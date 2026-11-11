package com.runiverse.running_service.domain.running.room.vo;

import com.runiverse.running_service.domain.running.room.exception.InvalidRunningRoomChatIdException;

// API chatId — 생성 순서대로 늘어나 정렬·커서·클라 중복 제거의 기준이다
public record RunningRoomChatId(Long value) {

    public RunningRoomChatId {
        if (value == null || value < 1) {
            throw new InvalidRunningRoomChatIdException();
        }
    }
}
