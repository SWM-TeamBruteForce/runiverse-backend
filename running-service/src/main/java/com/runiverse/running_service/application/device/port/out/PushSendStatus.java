package com.runiverse.running_service.application.device.port.out;

public enum PushSendStatus {
    SENT,
    // 앱이 지워졌거나 토큰이 만료돼 다시 보내도 닿지 않는다
    UNREGISTERED,
    FAILED
}
