package com.runiverse.running_service.application.running.port.out;

public interface RunningConnection {

    // 같은 유저의 다른 연결과 구분하는 식별자
    String id();

    // 마지막 연결이 이긴다 - 밀려난 쪽을 닫을 때 사용
    void closeSuperseded();

    // 진행 정보를 밀어 넣는다. 실패해도 던지지 않는다 —
    // 한 명에게 못 보냈다고 나머지 참가자의 브로드캐스트가 멈추면 안 된다
    void sendProgress(RunningProgress progress);
}
