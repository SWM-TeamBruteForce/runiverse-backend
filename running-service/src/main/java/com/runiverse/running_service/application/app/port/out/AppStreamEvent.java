package com.runiverse.running_service.application.app.port.out;

import com.runiverse.running_service.application.notification.query.list.GetUserNotificationsResult;

// 앱 스트림으로 나가는 이벤트 하나 — 종류에 따라 채워지는 값이 다르다.
// 알림 조립은 notification이 하고, 이 스트림은 받은 결과를 나르기만 한다
public record AppStreamEvent(AppStreamEventType type, GetUserNotificationsResult.Item notification) {

    // 새 알림 — 알림 종류가 곧 이벤트 이름이다. data는 목록(2-4) 항목과 같은 조립기로 만든 값이라 모양이 같다.
    // 알림 종류가 늘면 여기서 컴파일 에러가 난다 — 이벤트 이름을 정하지 않은 채 보내지 않는다
    public static AppStreamEvent notification(GetUserNotificationsResult.Item notification) {
        AppStreamEventType type = switch (notification.type()) {
            case FRIEND_REQUESTED -> AppStreamEventType.FRIEND_REQUESTED;
            case FRIEND_ACCEPTED -> AppStreamEventType.FRIEND_ACCEPTED;
            case RUNNING_INVITED -> AppStreamEventType.RUNNING_INVITED;
        };
        return new AppStreamEvent(type, notification);
    }
}
