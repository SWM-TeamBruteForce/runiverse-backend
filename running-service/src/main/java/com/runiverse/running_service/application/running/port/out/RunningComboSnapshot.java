package com.runiverse.running_service.application.running.port.out;

import com.runiverse.running_service.domain.common.vo.UserId;

import java.time.Instant;

// 판정 시점에 본 참가자 한 명 — 마지막으로 받은 누적 거리와 그 기준 시각, 그 구간의 속도,
// 그리고 참가자 상태(달리는 중·서 있음·관측 없음·일시정지·종료)를 정할 재료다.
// 속도가 있어야 배치 도착이 어긋난 참가자들의 누적 거리를 같은 시각으로 밀어 맞출 수 있다
public record RunningComboSnapshot(
        UserId userId,
        double meters,
        Instant recordedAt,
        // 마지막 두 배치 사이의 평균 속도(m/s) — 첫 배치는 이전 값이 없어 0이다
        double speedMetersPerSecond,
        // 의심점이 마지막으로 옮겨진 시각 — 한 번도 옮겨지지 않았으면 null(출발선)
        Instant movedAt,
        // 정확도 상한 이하의 새 좌표를 마지막으로 받은 시각 — 받은 적이 없으면 null
        Instant observedAt,
        // 일시정지 신호를 받았고 아직 관측이 다시 들어오지 않았다.
        // 스냅샷 값과 따로 저장된다 — 일시정지 이벤트와 위치 배치가 같은 값을 고쳐 쓰지 않게 한다
        boolean paused,
        // 러닝을 끝냈다. 좌표가 다시 와도 풀리지 않는다 — 끝난 사람의 좌표는 늦은 배치뿐이다
        boolean finished
) {

}
