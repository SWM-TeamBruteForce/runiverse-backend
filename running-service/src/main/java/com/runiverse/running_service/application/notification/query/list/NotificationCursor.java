package com.runiverse.running_service.application.notification.query.list;

import com.runiverse.running_service.application.notification.exception.InvalidNotificationCursorException;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

// 다음 페이지는 이 ID보다 오래된 알림부터 — ID가 생성 순서라 하나로 위치가 정해진다
public record NotificationCursor(Long beforeId) {

    // 클라에는 불투명한 값이다 — 형식을 바꿔도 계약이 깨지지 않게 감싼다
    public String encode() {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(beforeId.toString().getBytes(StandardCharsets.UTF_8));
    }

    public static NotificationCursor decode(String value) {
        try {
            String raw = new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
            long beforeId = Long.parseLong(raw);
            if (beforeId < 1) {
                throw new InvalidNotificationCursorException();
            }
            return new NotificationCursor(beforeId);
        } catch (IllegalArgumentException e) {
            throw new InvalidNotificationCursorException();
        }
    }
}
