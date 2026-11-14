package com.runiverse.running_service.domain.running.room.vo;

import com.runiverse.running_service.domain.running.room.exception.RunningRoomChatContentRequiredException;
import com.runiverse.running_service.domain.running.room.exception.RunningRoomChatContentTooLongException;

// 채팅 한 건의 내용 — 공백만 있는 글은 받지 않고, 쓴 그대로 둔다(회고 글과 같다).
// 지운 채팅도 이 값은 남는다 — 내려주지 않을 뿐이다
public record RunningRoomChatContent(String value) {

    private static final int MAX_LENGTH = 200;

    public RunningRoomChatContent {
        if (value == null || value.isBlank()) {
            throw new RunningRoomChatContentRequiredException();
        }
        if (value.length() > MAX_LENGTH) {
            throw new RunningRoomChatContentTooLongException();
        }
    }
}
