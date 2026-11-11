package com.runiverse.running_service.application.match.query.roomchatlist;

import java.util.UUID;

// 조회자는 토큰에서만 온다. cursor는 없으면 null이다(첫 페이지).
// limit은 컨트롤러가 기본값·상한을 적용한 값이다
public record GetRoomChatsQuery(UUID userId, Long runningRoomId, String cursor, int limit) {

}
