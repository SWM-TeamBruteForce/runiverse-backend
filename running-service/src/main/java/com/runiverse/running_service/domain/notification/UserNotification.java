package com.runiverse.running_service.domain.notification;

import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.notification.exception.SelfNotificationException;
import com.runiverse.running_service.domain.notification.exception.UserNotificationPayloadRequiredException;
import com.runiverse.running_service.domain.notification.exception.UserNotificationRecipientRequiredException;
import com.runiverse.running_service.domain.notification.vo.UserNotificationPayload;
import com.runiverse.running_service.domain.notification.vo.UserNotificationType;
import lombok.Getter;

// 받는 사람에게 일어난 사건 하나 — 사건의 기록이라 원 상태(요청·초대)가 바뀌어도 고치지 않는다.
// 읽음 처리는 받는 사람의 알림을 한 번에 바꾸는 벌크 갱신이라 이 객체를 거치지 않는다
@Getter
public class UserNotification {

    private final UserId userId;                     // 받는 사람
    private final UserNotificationPayload payload;   // 종류와 종류별 값 — type은 payload가 정한다

    private UserNotification(UserId userId, UserNotificationPayload payload) {
        if (userId == null) {
            throw new UserNotificationRecipientRequiredException();
        }
        if (payload == null) {
            throw new UserNotificationPayloadRequiredException();
        }
        if (userId.equals(payload.actorId())) {
            throw new SelfNotificationException();
        }
        this.userId = userId;
        this.payload = payload;
    }

    public static UserNotification of(UserId userId, UserNotificationPayload payload) {
        return new UserNotification(userId, payload);
    }

    public UserNotificationType type() {
        return payload.type();
    }
}
