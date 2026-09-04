package com.runiverse.running_service.application.match.port.out;

public interface MatchStreamConnection {

    // 같은 유저의 다른 연결과 구분하는 식별자
    String id();

    // 프록시 유휴 타임아웃을 막는 주석 라인. 실패하면 스스로 닫는다 —
    // 끊긴 단말을 걸러내는 유일한 수단이라 조용히 넘기면 좀비 연결이 쌓인다
    void keepAlive();

    // 마지막 연결이 이긴다 — 밀려난 쪽을 닫을 때 사용
    void closeSuperseded();
}
