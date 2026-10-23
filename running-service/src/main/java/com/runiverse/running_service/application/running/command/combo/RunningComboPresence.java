package com.runiverse.running_service.application.running.command.combo;

import com.runiverse.running_service.application.running.port.out.RunningComboSnapshot;

import java.time.Duration;
import java.time.Instant;

// 판정 시각에 본 참가자의 콤보용 상태 — 저장하지 않고 매번 계산한다.
// 시각만 지나도 바뀌는 값이라(15초 전 관측은 달리는 중, 20초 전은 관측 없음) 저장하면 아무 일이 없어도 틀려진다.
// 화면 상태(LiveRunningStatus)와 별개다 — 그쪽은 RUNNING_PROGRESS_UPDATED로 나가는 공개 값이다
public enum RunningComboPresence {
    FINISHED,
    PAUSED,
    UNOBSERVED,
    STANDING,
    MOVING;

    // 위에서부터 먼저 걸리는 것이다
    public static RunningComboPresence of(RunningComboSnapshot snapshot, Instant now, Duration freshness) {
        // 끝난 사람은 더 함께 달릴 수 없다 — 늦은 좌표가 와도 그대로다
        if (snapshot.finished()) {
            return FINISHED;
        }
        if (snapshot.paused()) {
            return PAUSED;
        }
        // 좌표가 안 오면 무슨 일이 있었는지 모른다 — 터널·연결 끊김
        if (!within(snapshot.observedAt(), now, freshness)) {
            return UNOBSERVED;
        }
        // 좌표는 오는데 의심점이 그대로다 — 신호 대기·출발선
        if (!within(snapshot.movedAt(), now, freshness)) {
            return STANDING;
        }
        return MOVING;
    }

    private static boolean within(Instant at, Instant now, Duration freshness) {
        return at != null && Duration.between(at, now).compareTo(freshness) <= 0;
    }
}
