package com.runiverse.running_service.presentation.notification.controller;

import com.runiverse.running_service.application.notification.command.read.ReadAllUserNotificationsCommand;
import com.runiverse.running_service.application.notification.port.in.GetUserNotificationsUsecase;
import com.runiverse.running_service.application.notification.port.in.ReadAllUserNotificationsUsecase;
import com.runiverse.running_service.application.notification.query.list.GetUserNotificationsQuery;
import com.runiverse.running_service.application.notification.query.list.GetUserNotificationsResult;
import com.runiverse.running_service.presentation.notification.response.UserNotificationsResponse;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("users")
@RequiredArgsConstructor
public class UserNotificationController {

    // 초과값은 거절하지 않고 상한으로 줄인다
    private static final int MAX_LIMIT = 50;

    private final GetUserNotificationsUsecase getUserNotificationsUsecase;
    private final ReadAllUserNotificationsUsecase readAllUserNotificationsUsecase;

    // 앱 진입 때 첫 페이지를 불러 배지(unreadCount)까지 그린다. 읽음 처리는 하지 않는다 — 모두 읽음 API가 한다
    @GetMapping("/me/notifications")
    public ResponseEntity<UserNotificationsResponse> getNotifications(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "입력값이 올바르지 않습니다.") int limit
    ) {
        GetUserNotificationsResult result = getUserNotificationsUsecase.handle(new GetUserNotificationsQuery(
                UUID.fromString(jwt.getSubject()),
                // 첫 페이지에 cursor=를 빈 값으로 실어 보내도 처음부터 읽는다
                cursor == null || cursor.isBlank() ? null : cursor,
                Math.min(limit, MAX_LIMIT)));
        return ResponseEntity.ok(UserNotificationsResponse.from(result));
    }

    // 알림함을 열 때 한 번 부른다 — 목록을 먼저 그린 뒤 불러야 새 알림을 표시할 수 있다
    @PostMapping("/me/notifications/read")
    public ResponseEntity<Void> readAllNotifications(@AuthenticationPrincipal Jwt jwt) {
        readAllUserNotificationsUsecase.handle(new ReadAllUserNotificationsCommand(UUID.fromString(jwt.getSubject())));
        return ResponseEntity.noContent().build();
    }
}
