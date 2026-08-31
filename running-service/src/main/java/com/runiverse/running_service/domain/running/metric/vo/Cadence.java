package com.runiverse.running_service.domain.running.metric.vo;

import com.runiverse.running_service.domain.running.metric.exception.CadenceOutOfRangeException;

public record Cadence(int stepsPerMinute) {

    private static final int MIN = 1;
    private static final int MAX = 300; // 엘리트도 겨우 가능하다고 한다.

    public Cadence {
        if (stepsPerMinute < MIN || stepsPerMinute > MAX) {
            throw new CadenceOutOfRangeException();
        }
    }

    // 기록에 담을 수 있는 케이던스인지 미리 묻는다 — 센서 오전송 표본 하나가 기록을 못 막게.
    // 범위를 밖에 한 번 더 적으면 어긋나므로 VO가 직접 답한다
    public static boolean isValid(int stepsPerMinute) {
        return stepsPerMinute >= MIN && stepsPerMinute <= MAX;
    }
}
