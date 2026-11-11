package com.runiverse.running_service.infrastructure.redis.match;

import java.util.UUID;

// 새 매칭 스트림이 이겼다는 알림 — 받은 인스턴스는 이 사용자의 연결 중 winnerConnectionId가 아닌 것을 닫는다
public record MatchSupersedeMessage(UUID userId, String winnerConnectionId) {

}
