package com.runiverse.running_service.application.match.port.in;

import com.runiverse.running_service.application.match.command.roomchatedit.EditRoomChatCommand;
import com.runiverse.running_service.application.match.port.out.RoomChat;

public interface EditRoomChatUsecase {

    // 고친 뒤의 채팅을 돌려준다 — 고친 사람의 화면은 이 응답으로 바뀐다(이벤트는 본인에게 가지 않는다).
    // 방 밖이거나 메시지가 없음·지움·남의 것이면 404, 러닝이 시작됐으면 409를 예외로 알린다
    RoomChat handle(EditRoomChatCommand command);
}
