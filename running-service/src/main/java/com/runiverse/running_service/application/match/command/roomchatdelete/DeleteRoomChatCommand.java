package com.runiverse.running_service.application.match.command.roomchatdelete;

import java.util.UUID;

// 지우는 사람은 토큰에서만 온다 — 보낸 본인인지는 유스케이스가 잠가 읽은 행과 맞춰 본다
public record DeleteRoomChatCommand(UUID userId, Long runningRoomId, Long chatId) {

}
