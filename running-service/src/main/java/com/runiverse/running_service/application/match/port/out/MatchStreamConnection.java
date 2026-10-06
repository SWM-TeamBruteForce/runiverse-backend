package com.runiverse.running_service.application.match.port.out;

public interface MatchStreamConnection {

    // 같은 유저의 다른 연결과 구분하는 식별자
    String id();

    // 프록시 유휴 타임아웃을 막는 주석 라인. 실패하면 스스로 닫는다 —
    // 끊긴 단말을 걸러내는 유일한 수단이라 조용히 넘기면 좀비 연결이 쌓인다
    void keepAlive();

    // 마지막 연결이 이긴다 — 밀려난 쪽을 닫을 때 사용
    void closeSuperseded();

    // 계정이 사라져 더 보낼 곳이 없다 — 닫으면 레지스트리에서 빠져 keep-alive도 멈춘다
    void closeForAccountDeletion();

    // 이벤트를 실어 보낸다. 와이어 형식(이벤트 이름·JSON)은 구현체가 정한다
    void send(MatchStreamEvent event);
}
