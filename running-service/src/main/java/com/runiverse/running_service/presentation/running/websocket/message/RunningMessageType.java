package com.runiverse.running_service.presentation.running.websocket.message;

import com.runiverse.running_service.presentation.common.websocket.WebSocketEnvelope;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;

public enum RunningMessageType {
    // C -> S
    HEALTH_CHECK,
    RUNNING_START,
    RUNNING_LOCATION_UPDATE,
    RUNNING_FINISH,

    // S -> C
    HEALTH_CHECKED,
    RUNNING_STARTED,
    PLAYER_RUNNING_PROGRESS_UPDATED,
    RUNNING_FINISHED,
    ERROR;

    // 모르는 타입은 예외가 아니라 빈 값이다 — 연결을 끊지 않고 ERROR로 돌려주기 위해서다.
    public static Optional<RunningMessageType> from(String raw) {
        return Arrays.stream(values())
                .filter(messageType -> messageType.name().equals(raw))
                .findFirst();
    }

    // 아무값도 보내지 않을때 사용
    public WebSocketEnvelope message() {
        return new WebSocketEnvelope(name(), Map.of());
    }

    // data를 담을 때 사용
    public WebSocketEnvelope message(Object data) {
        return new WebSocketEnvelope(name(), data);
    }
}
