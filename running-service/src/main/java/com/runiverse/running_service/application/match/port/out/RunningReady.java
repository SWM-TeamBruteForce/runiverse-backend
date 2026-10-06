package com.runiverse.running_service.application.match.port.out;

import java.time.LocalDateTime;

// 시작 통지의 전부 — RoomInfo를 싣지 않는다. 대기방은 이미 그려져 있고
// 클라가 여기서 필요한 건 "언제 쏠지"뿐이라, 시작 직전에 참가자 프로필을 다시 읽을 이유가 없다
public record RunningReady(
        Long runningRoomId,
        LocalDateTime scheduledStartAt,
        // 보낸 시점 기준 남은 시간. 시각 포맷이 초 단위까지라 밀리초를 시각으로는 실을 수 없고,
        // 클라가 기기 시계를 믿지 않고 타이머를 걸려면 이 값이어야 한다
        long startsInMs
) {

}
