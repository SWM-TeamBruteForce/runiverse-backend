package com.runiverse.running_service.application.running.port.out;

public interface PublishRunningComboPort {

    // 방 채널로 내보낸다 — 참가자를 든 인스턴스들이 받아 수신자별로 깎아서 로컬 연결에 밀어 넣는다.
    // 실패해도 던지지 않는다: 콤보는 화면 표시일 뿐이고 다음 배치가 현재 상태를 다시 나른다
    void publish(Long runningRoomId, RunningComboUpdate update);
}
