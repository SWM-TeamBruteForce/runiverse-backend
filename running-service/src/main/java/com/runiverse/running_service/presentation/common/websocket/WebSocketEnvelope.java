package com.runiverse.running_service.presentation.common.websocket;

import java.util.Map;

// event은 이벤트이고 data는 전달하는 데이터
public record WebSocketEnvelope(String event, Object data) {

    public static WebSocketEnvelope of(String event) {
        return new WebSocketEnvelope(event, Map.of());
    }
}
