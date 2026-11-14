package com.runiverse.running_service.application.match.port.in;

import com.runiverse.running_service.application.match.command.roomchatdelete.DeleteRoomChatCommand;

public interface DeleteRoomChatUsecase {

    // 돌려줄 것이 없다(204) — 지운 사람의 화면은 그 말풍선을 "삭제된 메시지"로 바꾼다(이벤트는 본인에게 가지 않는다).
    // 방 밖이거나 메시지가 없음·이미 지움·남의 것이면 404, 러닝이 시작됐으면 409를 예외로 알린다
    void handle(DeleteRoomChatCommand command);
}
