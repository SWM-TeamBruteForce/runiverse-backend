package com.runiverse.running_service.application.notification.port.out;

import com.runiverse.running_service.domain.common.vo.UserId;

public interface PublishUserNotificationPort {

    // 모든 인스턴스에 "이 사람에게 이 알림이 생겼다"를 알린다 — 받는 사람이 붙은 인스턴스가 조립해 밀어 넣는다.
    // 실패해도 던지지 않는다: 이미 커밋된 뒤라 되돌릴 것이 없고, 알림은 DB에 있어 다음 목록 조회가 복구한다
    void publish(UserId userId, Long notificationId);
}
