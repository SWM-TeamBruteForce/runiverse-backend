package com.runiverse.running_service.unit_test.relationship.application;

import com.github.f4b6a3.uuid.UuidCreator;
import com.runiverse.running_service.application.common.port.out.LoadUserByIdPort;
import com.runiverse.running_service.application.common.port.out.SendUserNotificationPort;
import com.runiverse.running_service.application.relationship.command.request.RequestFriendCommand;
import com.runiverse.running_service.application.relationship.command.request.RequestFriendHandler;
import com.runiverse.running_service.application.relationship.command.request.RequestFriendResult;
import com.runiverse.running_service.application.relationship.common.FriendRequestAcceptor;
import com.runiverse.running_service.application.relationship.exception.CannotFriendSelfException;
import com.runiverse.running_service.application.relationship.exception.FriendRequestAlreadyExistsException;
import com.runiverse.running_service.application.relationship.exception.FriendTargetNotFoundException;
import com.runiverse.running_service.application.relationship.port.out.EnsureUserRelationshipPairPort;
import com.runiverse.running_service.application.relationship.port.out.LoadUserRelationshipPort;
import com.runiverse.running_service.application.relationship.port.out.SaveUserRelationshipPort;
import com.runiverse.running_service.application.relationship.port.out.UserRelationshipPair;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.notification.vo.FriendRequestAcceptedPayload;
import com.runiverse.running_service.domain.notification.vo.FriendRequestReceivedPayload;
import com.runiverse.running_service.domain.relationship.UserRelationship;
import com.runiverse.running_service.domain.relationship.vo.RelationshipType;
import com.runiverse.running_service.domain.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@DisplayName("친구 요청 단위 테스트")
class RequestFriendHandlerTest {

    @Mock
    private LoadUserByIdPort loadUserByIdPort;

    @Mock
    private EnsureUserRelationshipPairPort ensureUserRelationshipPairPort;

    @Mock
    private LoadUserRelationshipPort loadUserRelationshipPort;

    @Mock
    private SaveUserRelationshipPort saveUserRelationshipPort;

    @Mock
    private SendUserNotificationPort sendUserNotificationPort;

    private RequestFriendHandler handler;

    private UUID userId;
    private UUID targetId;

    @BeforeEach
    void setUp() {
        // 서로 요청의 확정은 공용 컴포넌트가 맡는다 — 진짜를 끼워 저장 포트까지 이어지는지 본다
        handler = new RequestFriendHandler(
                loadUserByIdPort,
                ensureUserRelationshipPairPort,
                loadUserRelationshipPort,
                saveUserRelationshipPort,
                new FriendRequestAcceptor(saveUserRelationshipPort, sendUserNotificationPort),
                sendUserNotificationPort);
        userId = UuidCreator.getTimeOrderedEpoch();
        targetId = UuidCreator.getTimeOrderedEpoch();
    }

    @Test
    @DisplayName("관계가 없던 사이면 행을 확보한 뒤 잠가 읽고 두 행을 보낸 요청·받은 요청으로 바꾼다")
    void requestCreatesPendingPair() {
        // given
        givenTargetExists();
        UserRelationship mine = relationship(1L, userId, targetId, RelationshipType.NONE);
        UserRelationship theirs = relationship(2L, targetId, userId, RelationshipType.NONE);
        givenPair(mine, theirs);

        // when
        RequestFriendResult result = handler.handle(command());

        // then
        assertThat(result.relationshipType()).isEqualTo(RelationshipType.FRIEND_REQUEST_SENT);
        assertThat(mine.getType()).isEqualTo(RelationshipType.FRIEND_REQUEST_SENT);
        assertThat(theirs.getType()).isEqualTo(RelationshipType.FRIEND_REQUEST_RECEIVED);
        verify(saveUserRelationshipPort).save(mine);
        verify(saveUserRelationshipPort).save(theirs);
        // 행이 있어야 잠글 수 있으므로 확보가 잠금보다 먼저다
        InOrder order = inOrder(ensureUserRelationshipPairPort, loadUserRelationshipPort);
        order.verify(ensureUserRelationshipPairPort).ensurePair(new UserId(userId), new UserId(targetId));
        order.verify(loadUserRelationshipPort).loadPairForUpdate(new UserId(userId), new UserId(targetId));
    }

    @Test
    @DisplayName("새 요청이면 받는 사람에게 요청한 사람을 담은 친구 요청 알림을 보낸다")
    void requestSendsFriendRequestReceivedNotification() {
        // given
        givenTargetExists();
        givenPair(relationship(1L, userId, targetId, RelationshipType.NONE),
                relationship(2L, targetId, userId, RelationshipType.NONE));

        // when
        handler.handle(command());

        // then -> 받는 사람은 대상, 일으킨 사람은 요청한 본인이다
        verify(sendUserNotificationPort).send(new UserId(targetId),
                new FriendRequestReceivedPayload(new UserId(userId)));
    }

    @Test
    @DisplayName("상대가 먼저 요청해 뒀으면 새 요청 없이 바로 친구가 된다")
    void mutualRequestBecomesFriend() {
        // given
        givenTargetExists();
        UserRelationship mine = relationship(1L, userId, targetId, RelationshipType.FRIEND_REQUEST_RECEIVED);
        UserRelationship theirs = relationship(2L, targetId, userId, RelationshipType.FRIEND_REQUEST_SENT);
        givenPair(mine, theirs);

        // when
        RequestFriendResult result = handler.handle(command());

        // then
        assertThat(result.relationshipType()).isEqualTo(RelationshipType.FRIEND);
        assertThat(mine.getType()).isEqualTo(RelationshipType.FRIEND);
        assertThat(theirs.getType()).isEqualTo(RelationshipType.FRIEND);
        verify(saveUserRelationshipPort).save(mine);
        verify(saveUserRelationshipPort).save(theirs);
        // 요청이 아니라 수락으로 끝났으므로 요청 알림 대신 먼저 요청했던 상대에게 수락 알림을 보낸다
        verify(sendUserNotificationPort, never()).send(any(), any(FriendRequestReceivedPayload.class));
        verify(sendUserNotificationPort).send(new UserId(targetId),
                new FriendRequestAcceptedPayload(new UserId(userId)));
    }

    @ParameterizedTest
    @EnumSource(value = RelationshipType.class, names = {"FRIEND_REQUEST_SENT", "FRIEND"})
    @DisplayName("이미 요청했거나 이미 친구면 409이고 아무것도 바꾸지 않는다")
    void alreadyRequestedOrFriendIsConflict(RelationshipType type) {
        // given
        givenTargetExists();
        UserRelationship mine = relationship(1L, userId, targetId, type);
        UserRelationship theirs = relationship(2L, targetId, userId, counterpartOf(type));
        givenPair(mine, theirs);

        // when & then
        assertThatThrownBy(() -> handler.handle(command()))
                .isInstanceOf(FriendRequestAlreadyExistsException.class);
        assertThat(mine.getType()).isEqualTo(type);
        verifyNoInteractions(saveUserRelationshipPort, sendUserNotificationPort);
    }

    @Test
    @DisplayName("자기 자신에게는 요청할 수 없고 대상 확인도 행 확보도 하지 않는다")
    void selfRequestIsRejected() {
        // when & then -> 도메인까지 가면 자기 자신과의 행에서 500이 난다
        assertThatThrownBy(() -> handler.handle(new RequestFriendCommand(userId, userId)))
                .isInstanceOf(CannotFriendSelfException.class);
        verifyNoInteractions(loadUserByIdPort, ensureUserRelationshipPairPort,
                loadUserRelationshipPort, saveUserRelationshipPort, sendUserNotificationPort);
    }

    @Test
    @DisplayName("대상이 없으면 404이고 행을 확보하지 않는다")
    void missingTargetIsNotFound() {
        // given -> 행을 넣었다면 FK 위반으로 500이 났을 것이다
        given(loadUserByIdPort.loadById(new UserId(targetId))).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> handler.handle(command()))
                .isInstanceOf(FriendTargetNotFoundException.class);
        verifyNoInteractions(ensureUserRelationshipPairPort, loadUserRelationshipPort, saveUserRelationshipPort,
                sendUserNotificationPort);
    }

    @Test
    @DisplayName("확보한 뒤에도 행이 없으면 서버 문제라 저장하지 않는다")
    void missingRowAfterEnsureIsServerError() {
        // given -> 확보와 잠금 사이에 탈퇴가 커밋된 경우다
        givenTargetExists();
        givenPair(null, null);

        // when & then
        assertThatThrownBy(() -> handler.handle(command()))
                .isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(saveUserRelationshipPort, sendUserNotificationPort);
    }

    @Test
    @DisplayName("차단 관계는 아직 정하지 않아 저장하지 않고 실패한다")
    void blockedIsNotDecidedYet() {
        // given
        givenTargetExists();
        givenPair(relationship(1L, userId, targetId, RelationshipType.BLOCKED),
                relationship(2L, targetId, userId, RelationshipType.NONE));

        // when & then
        assertThatThrownBy(() -> handler.handle(command()))
                .isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(saveUserRelationshipPort, sendUserNotificationPort);
    }

    private RequestFriendCommand command() {
        return new RequestFriendCommand(userId, targetId);
    }

    private void givenTargetExists() {
        given(loadUserByIdPort.loadById(new UserId(targetId))).willReturn(Optional.of(mock(User.class)));
    }

    private void givenPair(UserRelationship mine, UserRelationship theirs) {
        given(loadUserRelationshipPort.loadPairForUpdate(new UserId(userId), new UserId(targetId)))
                .willReturn(new UserRelationshipPair(mine, theirs));
    }

    // 짝으로 쓰이는 상대 행의 값
    private static RelationshipType counterpartOf(RelationshipType type) {
        return type == RelationshipType.FRIEND_REQUEST_SENT ? RelationshipType.FRIEND_REQUEST_RECEIVED : type;
    }

    private UserRelationship relationship(Long id, UUID owner, UUID target, RelationshipType type) {
        return UserRelationship.builder()
                .userRelationshipId(id)
                .userId(owner)
                .targetId(target)
                .type(type)
                .build();
    }
}
