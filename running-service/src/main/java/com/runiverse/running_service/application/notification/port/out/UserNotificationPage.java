package com.runiverse.running_service.application.notification.port.out;

import java.util.List;

// 알림함 한 페이지 — 다음 페이지 기준은 걸러내기 전의 행으로 정한다.
// 되살리지 못한 행을 뺀 뒤에 세면 페이지가 짧아져 다음 페이지가 없다고 잘못 판단한다
public record UserNotificationPage(
        List<UserNotificationRow> rows,
        Long nextBeforeId   // 다음 페이지는 이 ID보다 오래된 알림부터. 더 없으면 null
) {

}
