package com.runiverse.running_service.application.notification.port.out;

import com.runiverse.running_service.domain.notification.vo.UserNotificationPayload;

import java.time.LocalDateTime;

// 알림함 한 줄 — payload는 어댑터가 되살려 검증까지 마친 값이다.
// 되살리지 못한 행은 어댑터가 빼고 넘긴다(깨진 행 하나로 알림함 전체가 막히지 않게)
public record UserNotificationRow(
        Long userNotificationId,
        UserNotificationPayload payload,
        boolean read,
        LocalDateTime createdAt
) {

}
