package com.runiverse.running_service.application.running.port.out;

// 상태 변경 요청의 결과. Redis가 돌려준 '바꾸기 전 값'으로 복원한다
public record LiveRunningStatusChange(LiveRunningStatus previous, LiveRunningStatus current) {

    // 판정은 Redis에서 원자적으로 끝났다 — 같은 규칙(canChangeFrom)을 다시 적용하면 결과가 그대로 나온다
    public static LiveRunningStatusChange of(LiveRunningStatus previous, LiveRunningStatus requested) {
        boolean applied = previous != requested && requested.canChangeFrom(previous);
        return new LiveRunningStatusChange(previous, applied ? requested : previous);
    }

    // 바뀌었을 때만 방에 알린다 — 같은 PAUSE가 여러 번 와도 상대 화면은 한 번만 바뀐다
    public boolean changed() {
        return previous != current;
    }
}
