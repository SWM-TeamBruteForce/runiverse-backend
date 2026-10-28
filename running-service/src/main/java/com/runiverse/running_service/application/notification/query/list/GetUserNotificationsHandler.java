package com.runiverse.running_service.application.notification.query.list;

import com.runiverse.running_service.application.notification.common.NotificationProperties;
import com.runiverse.running_service.application.notification.common.UserNotificationItemAssembler;
import com.runiverse.running_service.application.notification.port.in.GetUserNotificationsUsecase;
import com.runiverse.running_service.application.notification.port.out.CountUnreadUserNotificationsPort;
import com.runiverse.running_service.application.notification.port.out.LoadUserNotificationsPort;
import com.runiverse.running_service.application.notification.port.out.UserNotificationPage;
import com.runiverse.running_service.domain.common.vo.UserId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GetUserNotificationsHandler implements GetUserNotificationsUsecase {

    private final LoadUserNotificationsPort loadUserNotificationsPort;
    private final CountUnreadUserNotificationsPort countUnreadUserNotificationsPort;
    private final UserNotificationItemAssembler userNotificationItemAssembler;
    private final NotificationProperties notificationProperties;

    @Override
    public GetUserNotificationsResult handle(GetUserNotificationsQuery query) {
        UserId userId = new UserId(query.userId());
        Long beforeId = query.cursor() == null ? null : NotificationCursor.decode(query.cursor()).beforeId();
        // 목록과 안 읽은 수가 같은 경계를 써야 배지와 목록이 어긋나지 않는다
        LocalDateTime since = LocalDateTime.now().minus(notificationProperties.retention());

        // 1. 한 페이지를 읽는다 — 되살리지 못한 행은 어댑터가 이미 뺐다
        UserNotificationPage page = loadUserNotificationsPort.loadPage(userId, beforeId, since, query.limit());

        // 2. 지금 상태를 붙여 항목을 만든다 — 스트림 푸시와 같은 조립기를 써 모양이 어긋나지 않는다
        List<GetUserNotificationsResult.Item> items = userNotificationItemAssembler.assemble(userId, page.rows());

        // 3. 다음 페이지 기준은 걸러내기 전의 행으로 어댑터가 정했다
        String nextCursor = page.nextBeforeId() == null ? null : new NotificationCursor(page.nextBeforeId()).encode();
        long unreadCount = countUnreadUserNotificationsPort.countUnread(userId, since);
        return new GetUserNotificationsResult(items, nextCursor, unreadCount);
    }
}
