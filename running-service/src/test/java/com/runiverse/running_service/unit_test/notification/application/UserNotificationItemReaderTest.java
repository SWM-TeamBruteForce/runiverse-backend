package com.runiverse.running_service.unit_test.notification.application;

import com.github.f4b6a3.uuid.UuidCreator;
import com.runiverse.running_service.application.notification.common.UserNotificationItemAssembler;
import com.runiverse.running_service.application.notification.common.UserNotificationItemReader;
import com.runiverse.running_service.application.notification.port.out.LoadUserNotificationsPort;
import com.runiverse.running_service.application.notification.port.out.UserNotificationRow;
import com.runiverse.running_service.application.notification.query.list.GetUserNotificationsResult;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.notification.vo.FriendRequestReceivedPayload;
import com.runiverse.running_service.domain.notification.vo.UserNotificationType;
import com.runiverse.running_service.domain.relationship.vo.RelationshipType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@DisplayName("알림 한 건 읽기 단위 테스트")
class UserNotificationItemReaderTest {

    private static final UserId ME = new UserId(UuidCreator.getTimeOrderedEpoch());
    private static final UUID CHULSU = UuidCreator.getTimeOrderedEpoch();
    private static final UserNotificationRow ROW = new UserNotificationRow(912L,
            new FriendRequestReceivedPayload(new UserId(CHULSU)), false, LocalDateTime.of(2026, 10, 28, 12, 0));
    private static final GetUserNotificationsResult.Item ITEM = new GetUserNotificationsResult.Item(
            912L, UserNotificationType.FRIEND_REQUEST_RECEIVED, false, LocalDateTime.of(2026, 10, 28, 12, 0),
            new GetUserNotificationsResult.Actor(CHULSU, "철수", null, false, RelationshipType.FRIEND_REQUEST_RECEIVED),
            null);

    @Mock
    private LoadUserNotificationsPort loadUserNotificationsPort;

    @Mock
    private UserNotificationItemAssembler userNotificationItemAssembler;

    @InjectMocks
    private UserNotificationItemReader reader;

    @Test
    @DisplayName("한 건을 읽어 목록과 같은 조립기로 항목을 만든다")
    void readsAndAssemblesOne() {
        // given
        given(loadUserNotificationsPort.loadOne(ME, 912L)).willReturn(Optional.of(ROW));
        given(userNotificationItemAssembler.assemble(ME, List.of(ROW))).willReturn(List.of(ITEM));

        // when & then
        assertThat(reader.read(ME, 912L)).contains(ITEM);
    }

    @Test
    @DisplayName("알림을 읽지 못하면 조립하지 않고 비어 있다")
    void emptyWhenNotLoaded() {
        // given -> 없음·받는 사람 다름·되살리지 못함은 어댑터가 비워 준다
        given(loadUserNotificationsPort.loadOne(ME, 912L)).willReturn(Optional.empty());

        // when & then
        assertThat(reader.read(ME, 912L)).isEmpty();
        verifyNoInteractions(userNotificationItemAssembler);
    }

    @Test
    @DisplayName("조립기가 뺀 항목(초대 행 없음)이면 비어 있다")
    void emptyWhenAssemblerSkips() {
        // given
        given(loadUserNotificationsPort.loadOne(ME, 912L)).willReturn(Optional.of(ROW));
        given(userNotificationItemAssembler.assemble(ME, List.of(ROW))).willReturn(List.of());

        // when & then
        assertThat(reader.read(ME, 912L)).isEmpty();
    }

    @Test
    @DisplayName("읽기 트랜잭션을 스스로 연다")
    void opensReadOnlyTransaction() throws NoSuchMethodException {
        // when -> 조립기의 조회가 게으른 커서라 트랜잭션 밖에서는 먼저 닫힌다
        Transactional transactional = UserNotificationItemReader.class
                .getMethod("read", UserId.class, Long.class)
                .getAnnotation(Transactional.class);

        // then
        assertThat(transactional).isNotNull();
        assertThat(transactional.readOnly()).isTrue();
    }
}
