package com.runiverse.running_service.unit_test.relationship.application;

import com.github.f4b6a3.uuid.UuidCreator;
import com.runiverse.running_service.application.relationship.command.accept.AcceptFriendRequestCommand;
import com.runiverse.running_service.application.relationship.command.accept.AcceptFriendRequestHandler;
import com.runiverse.running_service.application.relationship.command.accept.AcceptFriendRequestResult;
import com.runiverse.running_service.application.relationship.common.FriendRequestAcceptor;
import com.runiverse.running_service.application.relationship.exception.FriendRequestNotFoundException;
import com.runiverse.running_service.application.relationship.port.out.LoadUserRelationshipPort;
import com.runiverse.running_service.application.relationship.port.out.SaveUserRelationshipPort;
import com.runiverse.running_service.application.relationship.port.out.UserRelationshipPair;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.relationship.UserRelationship;
import com.runiverse.running_service.domain.relationship.vo.RelationshipType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@DisplayName("친구 요청 수락 단위 테스트")
public class AcceptFriendRequestHandlerTest {

    @Mock
    private LoadUserRelationshipPort loadUserRelationshipPort;

    @Mock
    private SaveUserRelationshipPort saveUserRelationshipPort;

    private AcceptFriendRequestHandler handler;

    private UUID userId;
    private UUID requesterId;

    @BeforeEach
    void setUp() {
        // 짝 검증과 저장은 공용 컴포넌트가 맡는다 — 진짜를 끼워 저장 포트까지 이어지는지 본다
        handler = new AcceptFriendRequestHandler(
                loadUserRelationshipPort, new FriendRequestAcceptor(saveUserRelationshipPort));
        userId = UuidCreator.getTimeOrderedEpoch();
        requesterId = UuidCreator.getTimeOrderedEpoch();
    }

    @Test
    @DisplayName("받은 요청을 수락하면 두 행을 모두 친구로 바꿔 저장하고 FRIEND를 반환한다")
    void acceptReceivedRequest() {
        // given
        UserRelationship mine = relationship(1L, userId, requesterId, RelationshipType.FRIEND_REQUEST_RECEIVED);
        UserRelationship theirs = relationship(2L, requesterId, userId, RelationshipType.FRIEND_REQUEST_SENT);
        givenPair(mine, theirs);

        // when
        AcceptFriendRequestResult result = handler.handle(command());

        // then
        assertThat(result.relationshipType()).isEqualTo(RelationshipType.FRIEND);
        assertThat(mine.getType()).isEqualTo(RelationshipType.FRIEND);
        assertThat(theirs.getType()).isEqualTo(RelationshipType.FRIEND);
        verify(saveUserRelationshipPort).save(mine);
        verify(saveUserRelationshipPort).save(theirs);
    }

    @Test
    @DisplayName("이미 친구면 아무것도 저장하지 않고 같은 응답을 준다")
    void alreadyFriendIsIdempotent() {
        // given -> 응답을 못 받은 앱이 다시 보낸 경우다
        UserRelationship mine = relationship(1L, userId, requesterId, RelationshipType.FRIEND);
        UserRelationship theirs = relationship(2L, requesterId, userId, RelationshipType.FRIEND);
        givenPair(mine, theirs);

        // when
        AcceptFriendRequestResult result = handler.handle(command());

        // then
        assertThat(result.relationshipType()).isEqualTo(RelationshipType.FRIEND);
        verifyNoInteractions(saveUserRelationshipPort);
    }

    @Test
    @DisplayName("둘 사이에 행이 없으면 수락할 요청이 없다")
    void noRelationshipIsNotFound() {
        // given -> 처음 보는 사이이거나 상대가 탈퇴해 행이 CASCADE로 지워진 경우다
        givenPair(null, null);

        // when & then
        assertThatThrownBy(() -> handler.handle(command()))
                .isInstanceOf(FriendRequestNotFoundException.class);
        verifyNoInteractions(saveUserRelationshipPort);
    }

    @ParameterizedTest
    @EnumSource(value = RelationshipType.class, names = {"NONE", "FRIEND_REQUEST_SENT", "BLOCKED"})
    @DisplayName("내 행이 받은 요청이 아니면 수락할 요청이 없다")
    void notReceivedIsNotFound(RelationshipType type) {
        // given -> FRIEND_REQUEST_SENT는 내가 보낸 요청을 내가 수락하려는 경우다
        UserRelationship mine = relationship(1L, userId, requesterId, type);
        givenPair(mine, null);

        // when & then
        assertThatThrownBy(() -> handler.handle(command()))
                .isInstanceOf(FriendRequestNotFoundException.class);
        assertThat(mine.getType()).isEqualTo(type);
        verifyNoInteractions(saveUserRelationshipPort);
    }

    @Test
    @DisplayName("상대 행이 없으면 짝이 깨진 데이터라 아무것도 바꾸지 않고 실패한다")
    void missingCounterpartIsServerError() {
        // given
        UserRelationship mine = relationship(1L, userId, requesterId, RelationshipType.FRIEND_REQUEST_RECEIVED);
        givenPair(mine, null);

        // when & then -> 사용자 잘못이 아니므로 404가 아니다
        assertThatThrownBy(() -> handler.handle(command()))
                .isInstanceOf(IllegalStateException.class);
        assertThat(mine.getType()).isEqualTo(RelationshipType.FRIEND_REQUEST_RECEIVED);
        verifyNoInteractions(saveUserRelationshipPort);
    }

    @ParameterizedTest
    @EnumSource(value = RelationshipType.class, names = {"NONE", "FRIEND_REQUEST_RECEIVED", "FRIEND", "BLOCKED"})
    @DisplayName("상대 행이 보낸 요청이 아니면 짝이 깨진 데이터라 아무것도 바꾸지 않고 실패한다")
    void mismatchedCounterpartIsServerError(RelationshipType theirType) {
        // given
        UserRelationship mine = relationship(1L, userId, requesterId, RelationshipType.FRIEND_REQUEST_RECEIVED);
        UserRelationship theirs = relationship(2L, requesterId, userId, theirType);
        givenPair(mine, theirs);

        // when & then
        assertThatThrownBy(() -> handler.handle(command()))
                .isInstanceOf(IllegalStateException.class);
        assertThat(mine.getType()).isEqualTo(RelationshipType.FRIEND_REQUEST_RECEIVED);
        assertThat(theirs.getType()).isEqualTo(theirType);
        verifyNoInteractions(saveUserRelationshipPort);
    }

    private AcceptFriendRequestCommand command() {
        return new AcceptFriendRequestCommand(userId, requesterId);
    }

    // 수락은 언제나 (나, 요청자) 순서로 쌍을 잠가 읽는다
    private void givenPair(UserRelationship mine, UserRelationship theirs) {
        given(loadUserRelationshipPort.loadPairForUpdate(new UserId(userId), new UserId(requesterId)))
                .willReturn(new UserRelationshipPair(mine, theirs));
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
