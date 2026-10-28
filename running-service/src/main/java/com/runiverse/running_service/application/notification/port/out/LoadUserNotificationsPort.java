package com.runiverse.running_service.application.notification.port.out;

import com.runiverse.running_service.domain.common.vo.UserId;

import java.time.LocalDateTime;
import java.util.Optional;

public interface LoadUserNotificationsPort {

    // 받는 사람의 알림을 최신순(ID 역순)으로 limit개까지 읽는다.
    // beforeId가 있으면 그보다 오래된 것부터, since보다 먼저 쌓인 것(보관 기간 지남)은 빼고 읽는다.
    // payload를 되살리지 못한 행은 로그를 남기고 빼되, 다음 페이지 기준은 뺀 행까지 포함해 정한다
    UserNotificationPage loadPage(UserId userId, Long beforeId, LocalDateTime since, int limit);

    // 받는 사람의 알림 한 건 — 스트림으로 밀어 줄 때 Redis 메시지의 ID로 다시 읽는다.
    // 받는 사람이 다르거나 되살리지 못한 행이면 비어 있다(되살리지 못한 행은 로그를 남긴다)
    Optional<UserNotificationRow> loadOne(UserId userId, Long userNotificationId);
}
