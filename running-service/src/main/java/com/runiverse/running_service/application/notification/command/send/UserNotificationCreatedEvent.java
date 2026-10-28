package com.runiverse.running_service.application.notification.command.send;

import com.runiverse.running_service.domain.common.vo.UserId;

// 알림이 쌓였다는 사실 — 발행 시점에는 아직 커밋 전이다.
// 커밋 뒤에만 밖으로 내보내야 롤백된 요청의 알림이 밀려가지 않는다
public record UserNotificationCreatedEvent(UserId userId, Long notificationId) {

}
