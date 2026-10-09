package com.runiverse.running_service.application.running.common;

// 목표 도달 자동 종료의 판정 결과. 미달이면 남은 거리를 실어 클라가 "N m 더"로 보여준다
public record GoalCheck(boolean finished, int remainingMeters) {

    public static GoalCheck reached() {
        return new GoalCheck(true, 0);
    }

    public static GoalCheck pending(int remainingMeters) {
        return new GoalCheck(false, remainingMeters);
    }
}
