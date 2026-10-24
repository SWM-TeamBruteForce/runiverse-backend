package com.runiverse.running_service.unit_test.relationship.application;

import com.github.f4b6a3.uuid.UuidCreator;
import com.runiverse.running_service.application.relationship.command.cancel.CancelFriendRequestCommand;
import com.runiverse.running_service.application.relationship.command.cancel.CancelFriendRequestHandler;
import com.runiverse.running_service.application.relationship.common.RelationshipEnder;
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
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@DisplayName("친구 요청 취소·거절 단위 테스트")
public class CancelFriendRequestHandlerTest {

    @Mock
    private LoadUserRelationshipPort loadUserRelationshipPort;

    @Mock
    private SaveUserRelationshipPort saveUserRelationshipPort;

    private CancelFriendRequestHandler handler;

    private UUID userId;
    private UUID targetId;

    @BeforeEach
    void setUp() {
        // 짝 검증과 저장은 공용 컴포넌트가 맡는다 — 진짜를 끼워 저장 포트까지 이어지는지 본다
        handler = new CancelFriendRequestHandler(
                loadUserRelationshipPort, new RelationshipEnder(saveUserRelationshipPort));
        userId = UuidCreator.getTimeOrderedEpoch();
        targetId = UuidCreator.getTimeOrderedEpoch();
    }

    @Test
    @DisplayName("보낸 요청을 취소하면 두 행을 모두 NONE으로 바꿔 저장한다")
    void cancelSentRequest() {
        // given
        UserRelationship mine = relationship(1L, userId, targetId, RelationshipType.FRIEND_REQUEST_SENT);
        UserRelationship theirs = relationship(2L, targetId, userId, RelationshipType.FRIEND_REQUEST_RECEIVED);
        givenPair(mine, theirs);

        // when
        handler.handle(command());

        // then
        assertThat(mine.getType()).isEqualTo(RelationshipType.NONE);
        assertThat(theirs.getType()).isEqualTo(RelationshipType.NONE);
        verify(saveUserRelationshipPort).save(mine);
        verify(saveUserRelationshipPort).save(theirs);
    }

    @Test
    @DisplayName("받은 요청을 거절하면 두 행을 모두 NONE으로 바꿔 저장한다")
    void rejectReceivedRequest() {
        // given
        UserRelationship mine = relationship(1L, userId, targetId, RelationshipType.FRIEND_REQUEST_RECEIVED);
        UserRelationship theirs = relationship(2L, targetId, userId, RelationshipType.FRIEND_REQUEST_SENT);
        givenPair(mine, theirs);

        // when
        handler.handle(command());

        // then
        assertThat(mine.getType()).isEqualTo(RelationshipType.NONE);
        assertThat(theirs.getType()).isEqualTo(RelationshipType.NONE);
        verify(saveUserRelationshipPort).save(mine);
        verify(saveUserRelationshipPort).save(theirs);
    }

    @Test
    @DisplayName("그새 상대가 수락해 친구가 됐으면 친구 삭제로 두 행을 NONE으로 바꾼다")
    void friendIsEndedToo() {
        // given -> 요청을 보낸 뒤 상대가 수락하는 찰나에 취소를 누른 경우다
        UserRelationship mine = relationship(1L, userId, targetId, RelationshipType.FRIEND);
        UserRelationship theirs = relationship(2L, targetId, userId, RelationshipType.FRIEND);
        givenPair(mine, theirs);

        // when
        handler.handle(command());

        // then
        assertThat(mine.getType()).isEqualTo(RelationshipType.NONE);
        assertThat(theirs.getType()).isEqualTo(RelationshipType.NONE);
        verify(saveUserRelationshipPort).save(mine);
        verify(saveUserRelationshipPort).save(theirs);
    }

    @Test
    @DisplayName("둘 사이에 행이 없으면 끊을 관계가 없다")
    void noRelationshipIsNotFound() {
        // given -> 처음 보는 사이이거나 상대가 탈퇴해 행이 CASCADE로 지워진 경우다
        givenPair(null, null);

        // when & then
        assertThatThrownBy(() -> handler.handle(command()))
                .isInstanceOf(FriendRequestNotFoundException.class);
        verifyNoInteractions(saveUserRelationshipPort);
    }

    @ParameterizedTest
    @EnumSource(value = RelationshipType.class, names = {"NONE", "BLOCKED"})
    @DisplayName("내 행이 요청 중도 친구도 아니면 끊을 관계가 없다")
    void endedRelationshipIsNotFound(RelationshipType type) {
        // given -> NONE은 이미 취소·거절된 요청을 다시 지우는 경우다
        UserRelationship mine = relationship(1L, userId, targetId, type);
        givenPair(mine, null);

        // when & then
        assertThatThrownBy(() -> handler.handle(command()))
                .isInstanceOf(FriendRequestNotFoundException.class);
        assertThat(mine.getType()).isEqualTo(type);
        verifyNoInteractions(saveUserRelationshipPort);
    }

    @ParameterizedTest
    @EnumSource(value = RelationshipType.class, names = {"FRIEND_REQUEST_SENT", "FRIEND_REQUEST_RECEIVED", "FRIEND"})
    @DisplayName("상대 행이 없으면 짝이 깨진 데이터라 아무것도 바꾸지 않고 실패한다")
    void missingCounterpartIsServerError(RelationshipType myType) {
        // given
        UserRelationship mine = relationship(1L, userId, targetId, myType);
        givenPair(mine, null);

        // when & then -> 사용자 잘못이 아니므로 404가 아니다
        assertThatThrownBy(() -> handler.handle(command()))
                .isInstanceOf(IllegalStateException.class);
        assertThat(mine.getType()).isEqualTo(myType);
        verifyNoInteractions(saveUserRelationshipPort);
    }

    @ParameterizedTest
    @MethodSource("mismatchedPairs")
    @DisplayName("상대 행이 짝이 맞지 않으면 깨진 데이터라 아무것도 바꾸지 않고 실패한다")
    void mismatchedCounterpartIsServerError(RelationshipType myType, RelationshipType theirType) {
        // given
        UserRelationship mine = relationship(1L, userId, targetId, myType);
        UserRelationship theirs = relationship(2L, targetId, userId, theirType);
        givenPair(mine, theirs);

        // when & then
        assertThatThrownBy(() -> handler.handle(command()))
                .isInstanceOf(IllegalStateException.class);
        assertThat(mine.getType()).isEqualTo(myType);
        assertThat(theirs.getType()).isEqualTo(theirType);
        verifyNoInteractions(saveUserRelationshipPort);
    }

    // 같은 방향 요청끼리, 요청과 친구, 요청과 끝난 관계처럼 짝이 될 수 없는 조합
    private static Stream<Arguments> mismatchedPairs() {
        return Stream.of(
                Arguments.of(RelationshipType.FRIEND_REQUEST_SENT, RelationshipType.FRIEND_REQUEST_SENT),
                Arguments.of(RelationshipType.FRIEND_REQUEST_SENT, RelationshipType.NONE),
                Arguments.of(RelationshipType.FRIEND_REQUEST_RECEIVED, RelationshipType.FRIEND_REQUEST_RECEIVED),
                Arguments.of(RelationshipType.FRIEND_REQUEST_RECEIVED, RelationshipType.FRIEND),
                Arguments.of(RelationshipType.FRIEND, RelationshipType.FRIEND_REQUEST_SENT),
                Arguments.of(RelationshipType.FRIEND, RelationshipType.BLOCKED)
        );
    }

    private CancelFriendRequestCommand command() {
        return new CancelFriendRequestCommand(userId, targetId);
    }

    // 취소·거절은 언제나 (나, 상대) 순서로 쌍을 잠가 읽는다
    private void givenPair(UserRelationship mine, UserRelationship theirs) {
        given(loadUserRelationshipPort.loadPairForUpdate(new UserId(userId), new UserId(targetId)))
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
