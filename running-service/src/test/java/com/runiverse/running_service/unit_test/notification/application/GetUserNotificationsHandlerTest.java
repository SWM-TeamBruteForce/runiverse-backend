package com.runiverse.running_service.unit_test.notification.application;

import com.github.f4b6a3.uuid.UuidCreator;
import com.runiverse.running_service.application.notification.common.NotificationProperties;
import com.runiverse.running_service.application.notification.common.UserNotificationItemAssembler;
import com.runiverse.running_service.application.notification.exception.InvalidNotificationCursorException;
import com.runiverse.running_service.application.notification.port.out.CountUnreadUserNotificationsPort;
import com.runiverse.running_service.application.notification.port.out.LoadUserNotificationsPort;
import com.runiverse.running_service.application.notification.port.out.UserNotificationPage;
import com.runiverse.running_service.application.notification.port.out.UserNotificationRow;
import com.runiverse.running_service.application.notification.query.list.GetUserNotificationsHandler;
import com.runiverse.running_service.application.notification.query.list.GetUserNotificationsQuery;
import com.runiverse.running_service.application.notification.query.list.GetUserNotificationsResult;
import com.runiverse.running_service.application.notification.query.list.NotificationCursor;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.notification.vo.FriendRequestedPayload;
import com.runiverse.running_service.domain.notification.vo.UserNotificationType;
import com.runiverse.running_service.domain.relationship.vo.RelationshipType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@DisplayName("알림 목록 조회 단위 테스트")
class GetUserNotificationsHandlerTest {

    private static final UUID ME = UuidCreator.getTimeOrderedEpoch();
    private static final UUID CHULSU = UuidCreator.getTimeOrderedEpoch();
    private static final int LIMIT = 20;
    private static final Duration RETENTION = Duration.ofDays(30);
    private static final UserNotificationRow ROW = new UserNotificationRow(912L,
            new FriendRequestedPayload(new UserId(CHULSU)), false, LocalDateTime.of(2026, 10, 28, 12, 0));
    private static final GetUserNotificationsResult.Item ITEM = new GetUserNotificationsResult.Item(
            912L, UserNotificationType.FRIEND_REQUESTED, false, LocalDateTime.of(2026, 10, 28, 12, 0),
            new GetUserNotificationsResult.Actor(CHULSU, "철수", null, false, RelationshipType.FRIEND_REQUEST_RECEIVED),
            null);

    @Mock
    private LoadUserNotificationsPort loadUserNotificationsPort;

    @Mock
    private CountUnreadUserNotificationsPort countUnreadUserNotificationsPort;

    @Mock
    private UserNotificationItemAssembler userNotificationItemAssembler;

    private GetUserNotificationsHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GetUserNotificationsHandler(loadUserNotificationsPort, countUnreadUserNotificationsPort,
                userNotificationItemAssembler, new NotificationProperties(RETENTION));
    }

    @Test
    @DisplayName("한 페이지를 읽어 조립기로 항목을 만들고 안 읽은 수를 함께 준다")
    void assemblesPageWithUnreadCount() {
        // given
        given(loadUserNotificationsPort.loadPage(eq(new UserId(ME)), isNull(), any(), eq(LIMIT)))
                .willReturn(new UserNotificationPage(List.of(ROW), null));
        given(userNotificationItemAssembler.assemble(new UserId(ME), List.of(ROW))).willReturn(List.of(ITEM));
        given(countUnreadUserNotificationsPort.countUnread(eq(new UserId(ME)), any())).willReturn(3L);

        // when
        GetUserNotificationsResult result = handler.handle(new GetUserNotificationsQuery(ME, null, LIMIT));

        // then -> 스트림 전송과 같은 조립기를 써 모양이 어긋나지 않는다
        assertThat(result.items()).containsExactly(ITEM);
        assertThat(result.nextCursor()).isNull();
        assertThat(result.unreadCount()).isEqualTo(3L);
    }

    @Test
    @DisplayName("다음 페이지가 있으면 어댑터가 정한 기준으로 커서를 만든다")
    void encodesNextCursor() {
        // given
        given(loadUserNotificationsPort.loadPage(eq(new UserId(ME)), isNull(), any(), eq(LIMIT)))
                .willReturn(new UserNotificationPage(List.of(ROW), 912L));

        // when
        GetUserNotificationsResult result = handler.handle(new GetUserNotificationsQuery(ME, null, LIMIT));

        // then
        assertThat(NotificationCursor.decode(result.nextCursor()).beforeId()).isEqualTo(912L);
    }

    @Test
    @DisplayName("커서를 풀어 그 ID보다 오래된 알림을 보관 기간 안에서 읽고, 안 읽은 수도 같은 경계로 센다")
    void decodesCursorAndUsesSameSince() {
        // given
        given(loadUserNotificationsPort.loadPage(eq(new UserId(ME)), eq(500L), any(), eq(LIMIT)))
                .willReturn(new UserNotificationPage(List.of(), null));
        LocalDateTime before = LocalDateTime.now();

        // when
        handler.handle(new GetUserNotificationsQuery(ME, new NotificationCursor(500L).encode(), LIMIT));

        // then
        ArgumentCaptor<LocalDateTime> pageSince = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> countSince = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(loadUserNotificationsPort).loadPage(eq(new UserId(ME)), eq(500L), pageSince.capture(), eq(LIMIT));
        verify(countUnreadUserNotificationsPort).countUnread(eq(new UserId(ME)), countSince.capture());
        assertThat(pageSince.getValue()).isEqualTo(countSince.getValue());
        assertThat(pageSince.getValue()).isBetween(before.minus(RETENTION), LocalDateTime.now().minus(RETENTION));
    }

    @Test
    @DisplayName("깨진 커서는 400으로 거절하고 아무것도 읽지 않는다")
    void rejectsBrokenCursor() {
        // when & then
        assertThatThrownBy(() -> handler.handle(new GetUserNotificationsQuery(ME, "!!!", LIMIT)))
                .isInstanceOf(InvalidNotificationCursorException.class);
        verifyNoInteractions(loadUserNotificationsPort, countUnreadUserNotificationsPort, userNotificationItemAssembler);
    }
}
