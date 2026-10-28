package com.runiverse.running_service.application.notification.port.out;

import com.runiverse.running_service.domain.notification.UserNotification;

public interface SaveUserNotificationPort {

    // 새 알림을 쌓고 생성된 ID를 돌려준다 — 알림을 만드는 동작과 같은 트랜잭션에서 부른다.
    // ID는 커밋 뒤 스트림으로 밀어 줄 때 받는 쪽 서버가 그 알림을 다시 읽는 열쇠다
    Long save(UserNotification notification);
}
