package com.runiverse.running_service.application.notification.port.out;

import com.runiverse.running_service.domain.common.vo.UserId;

import java.time.LocalDateTime;

public interface MarkUserNotificationsReadPort {

    // 받는 사람의 안 읽은 알림을 한 번에 읽음으로 바꾸고 바꾼 행 수를 돌려준다.
    // 벌크 갱신이라 @UpdateTimestamp를 거치지 않는다 — 갱신 시각은 호출자가 넘긴다
    int markAllRead(UserId userId, LocalDateTime now);
}
