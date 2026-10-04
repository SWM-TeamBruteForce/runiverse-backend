package com.runiverse.running_service.presentation.running.websocket.message;

// WS에는 @Valid 파이프라인이 없다 — 필드 검증은 핸들러가 직접 하고 INVALID_REQUEST로 돌려준다
public record RunningStartRequest(Long runningRoomId) {

    public boolean isValid() {
        return runningRoomId != null && runningRoomId > 0;
    }
}
