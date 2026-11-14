package com.runiverse.running_service.application.match.port.in;

import com.runiverse.running_service.application.match.command.roomchatsend.SendRoomChatCommand;
import com.runiverse.running_service.application.match.port.out.RoomChat;

public interface SendRoomChatUsecase {

    // 저장한 채팅을 그대로 돌려준다 — 보낸 사람의 화면은 이 응답으로 바뀐다(이벤트는 본인에게 가지 않는다).
    // 방 밖이면 404, 러닝이 시작됐으면 409를 예외로 알린다
    RoomChat handle(SendRoomChatCommand command);
}
