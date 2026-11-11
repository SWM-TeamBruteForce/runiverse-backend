package com.runiverse.running_service.presentation.match.response;

import com.runiverse.running_service.application.match.query.roomchatlist.GetRoomChatsResult;

import java.util.List;

// 5-E-1 응답 — items는 최신순이다. nextCursor가 null이면 방의 첫 메시지까지 다 받았다.
// 항목은 보내기 응답·ROOM_CHAT_* 이벤트와 같은 Chat 모양이다
public record RoomChatsResponse(
        List<RoomChatResponse> items,
        String nextCursor
) {

    public static RoomChatsResponse from(GetRoomChatsResult result) {
        return new RoomChatsResponse(
                result.items().stream().map(RoomChatResponse::from).toList(),
                result.nextCursor());
    }
}
