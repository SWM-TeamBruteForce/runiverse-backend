package com.runiverse.running_service.domain.running.player.vo;

import com.runiverse.running_service.domain.running.player.exception.InvalidPlayerStatusTransitionException;

import java.util.Set;

public enum RunningPlayerStatus {
    INVITED,
    JOINED,
    MATCHED_LEFT_PENALTY,
    MATCHED_LEFT_NO_PENALTY,
    RUNNING,
    RUNNING_LEFT_PENALTY,
    RUNNING_LEFT_NO_PENALTY,
    COMPLETED;

    // 변환 할 때 올바르게 변환되는가 확인
    public RunningPlayerStatus transitionTo(RunningPlayerStatus next) {
        if (!allowedNext().contains(next)) {
            throw new InvalidPlayerStatusTransitionException();
        }
        return next;
    }

    // 이탈 시 어느 값으로 갈지는 "지금 어느 단계인가 + 페널티 대상인가"로 결정된다.
    // 네 개 값 중 고르는 판단을 호출자에게 흘리지 않으려고 여기서 닫는다.
    public RunningPlayerStatus leaveWith(boolean penalty) {
        return switch (this) {
            case JOINED -> penalty ? MATCHED_LEFT_PENALTY : MATCHED_LEFT_NO_PENALTY;
            case RUNNING -> penalty ? RUNNING_LEFT_PENALTY : RUNNING_LEFT_NO_PENALTY;
            default -> throw new InvalidPlayerStatusTransitionException();
        };
    }

    public boolean isLeft() {
        return this == MATCHED_LEFT_PENALTY || this == MATCHED_LEFT_NO_PENALTY
                || this == RUNNING_LEFT_PENALTY || this == RUNNING_LEFT_NO_PENALTY;
    }

    public boolean hasPenalty() {
        return this == MATCHED_LEFT_PENALTY || this == RUNNING_LEFT_PENALTY;
    }

    // 러닝 단계에 들어간 상태인가 — 시작 전 이탈(MATCHED_LEFT_*)과 가른다
    public boolean hasStartedRunning() {
        return this == RUNNING || this == RUNNING_LEFT_PENALTY
                || this == RUNNING_LEFT_NO_PENALTY || this == COMPLETED;
    }

    public boolean isTerminal() {
        return allowedNext().isEmpty();
    }

    private Set<RunningPlayerStatus> allowedNext() {
        return switch (this) {
            case INVITED -> Set.of(JOINED);
            case JOINED -> Set.of(RUNNING, MATCHED_LEFT_PENALTY, MATCHED_LEFT_NO_PENALTY);
            case RUNNING -> Set.of(COMPLETED, RUNNING_LEFT_PENALTY, RUNNING_LEFT_NO_PENALTY);
            case MATCHED_LEFT_PENALTY, MATCHED_LEFT_NO_PENALTY,
                 RUNNING_LEFT_PENALTY, RUNNING_LEFT_NO_PENALTY, COMPLETED -> Set.of();
        };
    }
}
