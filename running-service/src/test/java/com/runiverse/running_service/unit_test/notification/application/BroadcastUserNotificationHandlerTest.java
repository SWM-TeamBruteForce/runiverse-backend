package com.runiverse.running_service.unit_test.notification.application;

import ch.qos.logback.classic.Level;
import com.github.f4b6a3.uuid.UuidCreator;
import com.runiverse.running_service.application.app.port.out.AppStreamConnection;
import com.runiverse.running_service.application.app.port.out.AppStreamEvent;
import com.runiverse.running_service.application.app.port.out.AppStreamPort;
import com.runiverse.running_service.application.notification.command.broadcast.BroadcastUserNotificationCommand;
import com.runiverse.running_service.application.notification.command.broadcast.BroadcastUserNotificationHandler;
import com.runiverse.running_service.application.notification.common.UserNotificationItemReader;
import com.runiverse.running_service.application.notification.query.list.GetUserNotificationsResult;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.notification.vo.UserNotificationType;
import com.runiverse.running_service.domain.relationship.vo.RelationshipType;
import com.runiverse.running_service.support.LogCapture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@DisplayName("알림 스트림 전송 단위 테스트")
class BroadcastUserNotificationHandlerTest {

    private static final UUID ME = UuidCreator.getTimeOrderedEpoch();
    private static final UUID CHULSU = UuidCreator.getTimeOrderedEpoch();
    private static final GetUserNotificationsResult.Item ITEM = new GetUserNotificationsResult.Item(
            912L, UserNotificationType.FRIEND_REQUESTED, false, LocalDateTime.of(2026, 10, 28, 12, 0),
            new GetUserNotificationsResult.Actor(CHULSU, "철수", null, false, RelationshipType.FRIEND_REQUEST_RECEIVED),
            null);

    @Mock
    private AppStreamPort appStreamPort;

    @Mock
    private UserNotificationItemReader userNotificationItemReader;

    @Mock
    private AppStreamConnection connection;

    private BroadcastUserNotificationHandler handler;
    private LogCapture log;

    @BeforeEach
    void setUp() {
        handler = new BroadcastUserNotificationHandler(appStreamPort, userNotificationItemReader);
        log = LogCapture.of(BroadcastUserNotificationHandler.class);
    }

    @AfterEach
    void tearDown() {
        log.stop();
    }

    @Test
    @DisplayName("받는 사람이 이 인스턴스에 붙어 있지 않으면 알림을 읽지도 남기지도 않는다")
    void skipsSilentlyWithoutConnection() {
        // given -> 대부분의 인스턴스가 이 경우다. 붙은 인스턴스가 따로 보낸다
        given(appStreamPort.find(new UserId(ME))).willReturn(Optional.empty());

        // when
        handler.handle(new BroadcastUserNotificationCommand(ME, 912L));

        // then
        verifyNoInteractions(userNotificationItemReader);
        assertThat(log.messages(Level.INFO)).isEmpty();
        assertThat(log.messages(Level.WARN)).isEmpty();
    }

    @Test
    @DisplayName("붙어 있으면 목록 항목 모양으로 읽어 알림 종류 이름의 이벤트로 보낸다")
    void sendsNotificationCreated() {
        // given
        given(appStreamPort.find(new UserId(ME))).willReturn(Optional.of(connection));
        given(connection.id()).willReturn("conn-1");
        given(userNotificationItemReader.read(new UserId(ME), 912L)).willReturn(Optional.of(ITEM));

        // when
        handler.handle(new BroadcastUserNotificationCommand(ME, 912L));

        // then
        verify(connection).send(AppStreamEvent.notification(ITEM));
        assertThat(AppStreamEvent.notification(ITEM).type().name()).isEqualTo("FRIEND_REQUESTED");
        assertThat(log.messages(Level.INFO))
                .containsExactly("[알림] 스트림 전송 성공 - notificationId=912, connectionId=conn-1");
    }

    @Test
    @DisplayName("붙어 있어도 알림을 읽지 못하면 보내지 않고 경고를 남긴다")
    void warnsWhenItemMissing() {
        // given
        given(appStreamPort.find(new UserId(ME))).willReturn(Optional.of(connection));
        given(userNotificationItemReader.read(new UserId(ME), 912L)).willReturn(Optional.empty());

        // when
        handler.handle(new BroadcastUserNotificationCommand(ME, 912L));

        // then
        verify(connection, never()).send(any());
        assertThat(log.messages(Level.WARN)).containsExactly("[알림] 스트림 전송 건너뜀: 알림 없음 - notificationId=912");
    }

    @Test
    @DisplayName("핸들러는 트랜잭션을 열지 않는다")
    void opensNoTransaction() throws NoSuchMethodException {
        // when & then -> 붙어 있지 않은 인스턴스가 메시지마다 DB 커넥션을 잡지 않는다. 읽기 트랜잭션은 리더가 연다
        assertThat(BroadcastUserNotificationHandler.class.getAnnotation(Transactional.class)).isNull();
        assertThat(BroadcastUserNotificationHandler.class
                .getMethod("handle", BroadcastUserNotificationCommand.class)
                .getAnnotation(Transactional.class)).isNull();
    }
}
