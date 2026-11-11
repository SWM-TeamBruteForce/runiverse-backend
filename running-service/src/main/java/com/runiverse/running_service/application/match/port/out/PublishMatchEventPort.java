package com.runiverse.running_service.application.match.port.out;

public interface PublishMatchEventPort {

    // 방 채널로 내보낸다 — 참가자를 든 인스턴스들이 받아 각자의 로컬 연결에 밀어 넣는다.
    // 실패해도 던지지 않는다: 방 상태 이벤트는 전체 상태라 다음 갱신이나 재연결 스냅샷이 복구하고,
    // 채팅은 변경분이라 다음 입장 때 목록(5-E-1)으로 맞춘다
    void publish(MatchStreamEvent event);
}
