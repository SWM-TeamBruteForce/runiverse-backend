package com.runiverse.running_service.application.running.port.out;

import java.util.UUID;

// 받는 사람 한 명의 화면에 뜨는 상대 한 명. 관계를 받는 사람 기준으로 돌려놓은 값이라
// 같은 관계라도 양쪽이 받는 값의 부호가 반대다
public record RunningComboPeer(
        UUID userId,
        // 양수면 상대가 앞, 음수면 뒤. 좌표상 거리가 아니라 누적 주행 거리의 차이다
        int gapMeters,
        int comboCount,
        int maxComboCount
) {

}
