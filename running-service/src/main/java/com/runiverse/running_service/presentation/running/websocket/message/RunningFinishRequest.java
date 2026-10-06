package com.runiverse.running_service.presentation.running.websocket.message;

public record RunningFinishRequest(Boolean forced) {

    public boolean isValid() {
        return forced != null;
    }
}
