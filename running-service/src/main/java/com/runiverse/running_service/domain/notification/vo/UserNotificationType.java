package com.runiverse.running_service.domain.notification.vo;

// 받는 사람에게 무슨 일이 일어났나 — 문구와 동작은 클라가 이 값으로 정한다.
// 값마다 payload 모양이 하나씩 정해져 있다(UserNotificationPayload 구현체)
public enum UserNotificationType {
    // 누군가 나에게 친구 요청을 보냈다
    FRIEND_REQUESTED,
    // 내가 보낸 친구 요청을 상대가 수락했다 — 서로 요청해 바로 성립한 경우도 포함한다
    FRIEND_ACCEPTED,
    // 누군가 나를 초대 방에 초대했다 — 다시 초대도 새로 쌓는다
    RUNNING_INVITED
}
