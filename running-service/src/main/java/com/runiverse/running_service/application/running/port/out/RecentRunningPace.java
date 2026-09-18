package com.runiverse.running_service.application.running.port.out;

// 최근 기록 한 건에서 평균 페이스를 다시 낼 재료. 기록별 avg_pace가 아니라
// 거리·시간을 받는다 — 합으로 나눠야 짧은 러닝이 평균을 끌고 가지 않는다
public record RecentRunningPace(int totalDistanceMeters, int totalDurationSeconds) {

}
