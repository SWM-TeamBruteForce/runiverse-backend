package com.runiverse.running_service.domain.running.room;

import com.runiverse.running_service.domain.running.room.vo.RunningRoomChatContent;
import com.runiverse.running_service.domain.running.room.vo.RunningRoomChatId;
import lombok.Getter;

// 채팅 하나를 고칠 때 남기는 고치기 직전 내용 — 운영(신고 대응) 전용이라 쓰기만 하고 되읽지 않는다.
// 채팅을 거쳐서만 생긴다(RunningRoomChat.edit). 고친 사람은 늘 보낸 사람이라 따로 두지 않는다
@Getter
public class RunningRoomChatHistory {

    private final RunningRoomChatId runningRoomChatId;
    private final RunningRoomChatContent content;

    private RunningRoomChatHistory(RunningRoomChatId runningRoomChatId, RunningRoomChatContent content) {
        this.runningRoomChatId = runningRoomChatId;
        this.content = content;
    }

    static RunningRoomChatHistory of(RunningRoomChatId runningRoomChatId, RunningRoomChatContent previousContent) {
        return new RunningRoomChatHistory(runningRoomChatId, previousContent);
    }
}
