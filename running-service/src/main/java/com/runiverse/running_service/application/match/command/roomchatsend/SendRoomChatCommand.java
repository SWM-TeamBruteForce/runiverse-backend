package com.runiverse.running_service.application.match.command.roomchatsend;

import java.util.UUID;

// 보낸 사람은 토큰에서만 온다. 내용 길이·공백은 Request DTO가 먼저 거르고,
// 도메인 VO(RunningRoomChatContent)가 한 번 더 지킨다
public record SendRoomChatCommand(UUID userId, Long runningRoomId, String content) {

}
