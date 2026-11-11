package com.runiverse.running_service.application.match.query.roomchatlist;

import com.runiverse.running_service.application.match.port.out.RoomChat;

import java.util.List;

// items는 최신순이다 — 화면은 뒤집어 아래가 최신이 되게 그린다.
// nextCursor가 null이면 방의 첫 메시지까지 다 받았다
public record GetRoomChatsResult(List<RoomChat> items, String nextCursor) {

}
