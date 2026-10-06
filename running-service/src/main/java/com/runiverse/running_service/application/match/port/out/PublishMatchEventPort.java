package com.runiverse.running_service.application.match.port.out;

public interface PublishMatchEventPort {

    // 방 채널로 내보낸다 — 참가자를 든 인스턴스들이 받아 각자의 로컬 연결에 밀어 넣는다.
    // 실패해도 던지지 않는다: 이벤트가 전체 상태라 다음 갱신이나 재연결 스냅샷이 복구한다
    void publish(MatchStreamEvent event);
}
