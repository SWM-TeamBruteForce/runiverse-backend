package com.runiverse.running_service.application.match.command.stream;

import java.util.UUID;

// 다른 인스턴스에서 새 매칭 스트림이 열렸다는 알림 — winnerConnectionId가 아닌 이 사용자의 연결은 닫는다
public record CloseSupersededMatchStreamCommand(UUID userId, String winnerConnectionId) {

}
