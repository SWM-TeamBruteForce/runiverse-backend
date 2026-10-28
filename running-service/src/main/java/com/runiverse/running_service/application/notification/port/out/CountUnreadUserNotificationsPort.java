package com.runiverse.running_service.application.notification.port.out;

import com.runiverse.running_service.domain.common.vo.UserId;

import java.time.LocalDateTime;

public interface CountUnreadUserNotificationsPort {

    // 받는 사람의 안 읽은 알림 수 — 목록과 같은 since를 받아야 배지와 목록이 어긋나지 않는다
    long countUnread(UserId userId, LocalDateTime since);
}
