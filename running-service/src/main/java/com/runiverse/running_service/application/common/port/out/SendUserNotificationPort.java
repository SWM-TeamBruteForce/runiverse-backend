package com.runiverse.running_service.application.common.port.out;

import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.notification.vo.UserNotificationPayload;

// 받는 사람에게 인앱 알림을 보낸다. 부르는 쪽은 저장·스트림 전달을 알지 않는다.
// 친구 요청·수락이 먼저 쓰고, 러닝 초대가 같은 입구를 쓴다
public interface SendUserNotificationPort {

    // 알림을 만드는 동작과 같은 트랜잭션에서 부른다 — 동작이 롤백되면 알림도 함께 사라진다
    void send(UserId userId, UserNotificationPayload payload);
}
