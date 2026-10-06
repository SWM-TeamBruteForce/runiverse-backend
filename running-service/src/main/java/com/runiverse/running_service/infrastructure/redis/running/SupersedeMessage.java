package com.runiverse.running_service.infrastructure.redis.running;

import java.util.UUID;

// 새 연결이 이겼다는 통지 - 이 sessionId만 남기고 같은 유저의 다른 연결은 닫는다
public record SupersedeMessage(UUID userId, String winnerSessionId) {

}
