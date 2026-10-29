package com.runiverse.running_service.application.notification.common;

import com.runiverse.running_service.application.notification.port.out.LoadUserNotificationsPort;
import com.runiverse.running_service.application.notification.query.list.GetUserNotificationsResult;
import com.runiverse.running_service.domain.common.vo.UserId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

// 알림 한 건을 읽어 목록 항목 모양으로 만든다 — 스트림 전송(연결을 확인한 뒤)과 기기 푸시가 부른다.
// 트랜잭션을 여기서 연다: 조립기의 조회가 게으른 커서를 써서 트랜잭션 밖에서는 먼저 닫힌다.
// 스트림 전송 핸들러에 걸면 연결이 없는 인스턴스도 메시지마다 DB 커넥션을 잡는다
@Component
@RequiredArgsConstructor
public class UserNotificationItemReader {

    private final LoadUserNotificationsPort loadUserNotificationsPort;
    private final UserNotificationItemAssembler userNotificationItemAssembler;

    // 없거나, 받는 사람이 다르거나, 되살리지 못했거나, 초대 행이 사라졌으면 비어 있다
    @Transactional(readOnly = true)
    public Optional<GetUserNotificationsResult.Item> read(UserId userId, Long userNotificationId) {
        return loadUserNotificationsPort.loadOne(userId, userNotificationId)
                .flatMap(row -> userNotificationItemAssembler.assemble(userId, List.of(row)).stream().findFirst());
    }
}
