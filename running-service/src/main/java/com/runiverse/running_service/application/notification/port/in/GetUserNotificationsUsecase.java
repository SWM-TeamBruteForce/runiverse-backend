package com.runiverse.running_service.application.notification.port.in;

import com.runiverse.running_service.application.notification.query.list.GetUserNotificationsQuery;
import com.runiverse.running_service.application.notification.query.list.GetUserNotificationsResult;

public interface GetUserNotificationsUsecase {

    GetUserNotificationsResult handle(GetUserNotificationsQuery query);
}
