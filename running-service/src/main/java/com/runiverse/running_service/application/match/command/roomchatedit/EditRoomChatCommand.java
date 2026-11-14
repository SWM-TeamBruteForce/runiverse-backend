package com.runiverse.running_service.application.match.command.roomchatedit;

import java.util.UUID;

// 고치는 사람은 토큰에서만 온다 — 보낸 본인인지는 유스케이스가 잠가 읽은 행과 맞춰 본다.
// 내용 길이·공백은 Request DTO가 먼저 거르고, 도메인 VO(RunningRoomChatContent)가 한 번 더 지킨다
public record EditRoomChatCommand(UUID userId, Long runningRoomId, Long chatId, String content) {

}
