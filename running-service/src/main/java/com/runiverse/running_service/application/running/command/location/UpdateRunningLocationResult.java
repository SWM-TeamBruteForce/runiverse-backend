package com.runiverse.running_service.application.running.command.location;

public record UpdateRunningLocationResult(boolean finished) {

    // 팩토리 이름에 of를 붙인다 — finished()는 record가 만드는 접근자 이름이라 겹치면 컴파일되지 않는다
    public static UpdateRunningLocationResult ofRunning() {
        return new UpdateRunningLocationResult(false);
    }

    public static UpdateRunningLocationResult ofFinished() {
        return new UpdateRunningLocationResult(true);
    }
}
