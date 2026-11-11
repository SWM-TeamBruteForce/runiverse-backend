package com.runiverse.running_service.application.match.port.in;

import com.runiverse.running_service.application.match.query.roomchatlist.GetRoomChatsQuery;
import com.runiverse.running_service.application.match.query.roomchatlist.GetRoomChatsResult;

public interface GetRoomChatsUsecase {

    // 방 밖이면 404, 러닝이 시작됐으면 409, 커서가 깨졌으면 400을 예외로 알린다
    GetRoomChatsResult handle(GetRoomChatsQuery query);
}
