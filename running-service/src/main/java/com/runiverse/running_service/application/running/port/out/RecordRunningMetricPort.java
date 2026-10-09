package com.runiverse.running_service.application.running.port.out;

import com.runiverse.running_service.application.running.common.TrackFilterSummary;

// 러닝 종료 안에서만 보이는 판정 결과를 센다 — 메트릭 이름·태그는 구현체가 정한다.
// 트랙 필터의 판정값이 휴리스틱이라, 실제 러닝에서 무엇을 얼마나 거르는지 보고 조정하려고 둔다
public interface RecordRunningMetricPort {

    // 기록을 확정할 때마다 한 번 — 미뤄진 종료는 다시 시도할 때마다 세지 않도록 넣지 않는다
    void trackFiltered(TrackFilterSummary summary);

    // 자동 종료 재확인 — 확정했는지, 확정 거리가 모자라 미뤘는지
    void autoFinishChecked(boolean finished);

    // 다 뛰었다고 보고 누른 종료(forced=false)를 미뤘을 때 남은 거리 —
    // 화면 거리와 서버 확정 거리가 얼마나 어긋나는지 본다
    void userFinishDeferred(int remainingMeters);
}
