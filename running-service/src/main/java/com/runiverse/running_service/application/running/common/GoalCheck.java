package com.runiverse.running_service.application.running.common;

// 확정 거리로 낸 종료 판정 — 끝냈는지, 못 끝냈으면 남은 거리.
// 남은 거리는 사용자 종료(forced=false)만 RUNNING_GOAL_PENDING으로 알린다. 자동 종료는 알리지 않는다
public record GoalCheck(boolean finished, int remainingMeters) {

    public static GoalCheck reached() {
        return new GoalCheck(true, 0);
    }

    public static GoalCheck pending(int remainingMeters) {
        return new GoalCheck(false, remainingMeters);
    }
}
