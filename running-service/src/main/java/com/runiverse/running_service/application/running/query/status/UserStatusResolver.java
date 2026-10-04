package com.runiverse.running_service.application.running.query.status;

import com.runiverse.running_service.application.running.port.out.UserStatusRow;

import java.time.Duration;
import java.time.LocalDateTime;

public final class UserStatusResolver {

    private UserStatusResolver() {
    }

    // 상태 컬럼을 따로 두지 않는다 — 같은 사실이 두 벌이 되면 갱신 지점이 흩어져 어긋난다.
    // 참가자 상태(JOINED/RUNNING)는 보지 않는다: 방이 STARTED면 클라가 할 일은 같고
    // RUNNING_START가 멱등이라 둘을 구분할 이유가 없다
    public static UserRunningStatus resolve(
            UserStatusRow row, LocalDateTime now, Duration closeOffset) {
        return switch (row.roomStatus()) {
            case STARTED -> UserRunningStatus.RUNNING;
            case MATCHED -> UserRunningStatus.READY;
            // 마감은 방 상태가 아니라 시각으로 판정한다 — 확정 예약이 늦게 깨는 틈에
            // 대기 화면을 그리게 두면 잠시 뒤 화면이 다시 바뀐다
            case MATCHING -> now.isBefore(row.scheduledStartAt().minus(closeOffset))
                    ? UserRunningStatus.WAITING
                    : UserRunningStatus.READY;
            // 닫힌 방의 활성 신청은 방이 닫힌 직후 강제 종료가 정리하기 전의 잠깐뿐이다 — 할 일이 없으니 IDLE
            case FINISHED, CANCELLED -> UserRunningStatus.IDLE;
        };
    }
}
