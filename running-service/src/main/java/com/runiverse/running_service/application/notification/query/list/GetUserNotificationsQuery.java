package com.runiverse.running_service.application.notification.query.list;

import java.util.UUID;

// cursor는 없으면 null이다(첫 페이지). limit은 컨트롤러가 기본값·상한을 적용한 값이다
public record GetUserNotificationsQuery(UUID userId, String cursor, int limit) {

}
