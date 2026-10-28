package com.runiverse.running_service.application.notification.command.read;

import com.runiverse.running_service.application.notification.port.in.ReadAllUserNotificationsUsecase;
import com.runiverse.running_service.application.notification.port.out.MarkUserNotificationsReadPort;
import com.runiverse.running_service.domain.common.vo.UserId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class ReadAllUserNotificationsHandler implements ReadAllUserNotificationsUsecase {

    private final MarkUserNotificationsReadPort markUserNotificationsReadPort;

    // 알림함을 열 때 부른다 — 항목별 읽음은 없다. 안 읽은 알림이 없어도 성공한다
    @Override
    public void handle(ReadAllUserNotificationsCommand command) {
        int readCount = markUserNotificationsReadPort.markAllRead(
                new UserId(command.userId()), LocalDateTime.now());
        log.info("[알림] 모두 읽음 성공 - readCount={}", readCount);
    }
}
