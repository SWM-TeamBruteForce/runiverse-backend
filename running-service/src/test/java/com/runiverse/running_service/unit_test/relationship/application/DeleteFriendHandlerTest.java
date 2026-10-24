package com.runiverse.running_service.unit_test.relationship.application;

import com.github.f4b6a3.uuid.UuidCreator;
import com.runiverse.running_service.application.relationship.command.delete.DeleteFriendCommand;
import com.runiverse.running_service.application.relationship.command.delete.DeleteFriendHandler;
import com.runiverse.running_service.application.relationship.common.RelationshipEnder;
import com.runiverse.running_service.application.relationship.exception.FriendNotFoundException;
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
@DisplayName("친구 삭제 단위 테스트")
public class DeleteFriendHandlerTest {

    @Mock
    private LoadUserRelationshipPort loadUserRelationshipPort;

    @Mock
    private SaveUserRelationshipPort saveUserRelationshipPort;

    private DeleteFriendHandler handler;

    private UUID userId;
    private UUID friendId;

    @BeforeEach
    void setUp() {
        // 짝 검증과 저장은 공용 컴포넌트가 맡는다 — 진짜를 끼워 저장 포트까지 이어지는지 본다
        handler = new DeleteFriendHandler(loadUserRelationshipPort, new RelationshipEnder(saveUserRelationshipPort));
        userId = UuidCreator.getTimeOrderedEpoch();
        friendId = UuidCreator.getTimeOrderedEpoch();
    }

    @Test
    @DisplayName("친구를 삭제하면 두 행을 모두 NONE으로 바꿔 저장한다")
    void deleteFriend() {
        // given
        UserRelationship mine = relationship(1L, userId, friendId, RelationshipType.FRIEND);
        UserRelationship theirs = relationship(2L, friendId, userId, RelationshipType.FRIEND);
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
    @DisplayName("둘 사이에 행이 없으면 삭제할 친구가 없다")
    void noRelationshipIsNotFound() {
        // given -> 처음 보는 사이이거나 상대가 탈퇴해 행이 CASCADE로 지워진 경우다
        givenPair(null, null);

        // when & then
        assertThatThrownBy(() -> handler.handle(command()))
                .isInstanceOf(FriendNotFoundException.class);
        verifyNoInteractions(saveUserRelationshipPort);
    }

    @ParameterizedTest
    @EnumSource(value = RelationshipType.class, names = {"NONE", "FRIEND_REQUEST_SENT", "FRIEND_REQUEST_RECEIVED", "BLOCKED"})
    @DisplayName("내 행이 친구가 아니면 삭제할 친구가 없다")
    void notFriendIsNotFound(RelationshipType type) {
        // given -> 요청 중인 관계는 friend-request로 끊는다. NONE은 이미 삭제한 친구를 다시 지우는 경우다
        UserRelationship mine = relationship(1L, userId, friendId, type);
        givenPair(mine, null);

        // when & then
        assertThatThrownBy(() -> handler.handle(command()))
                .isInstanceOf(FriendNotFoundException.class);
        assertThat(mine.getType()).isEqualTo(type);
        verifyNoInteractions(saveUserRelationshipPort);
    }

    @ParameterizedTest
    @EnumSource(value = RelationshipType.class, names = {"NONE", "FRIEND_REQUEST_SENT", "FRIEND_REQUEST_RECEIVED", "BLOCKED"})
    @DisplayName("상대 행이 친구가 아니면 짝이 깨진 데이터라 아무것도 바꾸지 않고 실패한다")
    void mismatchedCounterpartIsServerError(RelationshipType theirType) {
        // given
        UserRelationship mine = relationship(1L, userId, friendId, RelationshipType.FRIEND);
        UserRelationship theirs = relationship(2L, friendId, userId, theirType);
        givenPair(mine, theirs);

        // when & then -> 사용자 잘못이 아니므로 404가 아니다
        assertThatThrownBy(() -> handler.handle(command()))
                .isInstanceOf(IllegalStateException.class);
        assertThat(mine.getType()).isEqualTo(RelationshipType.FRIEND);
        assertThat(theirs.getType()).isEqualTo(theirType);
        verifyNoInteractions(saveUserRelationshipPort);
    }

    private DeleteFriendCommand command() {
        return new DeleteFriendCommand(userId, friendId);
    }

    // 삭제는 언제나 (나, 친구) 순서로 쌍을 잠가 읽는다
    private void givenPair(UserRelationship mine, UserRelationship theirs) {
        given(loadUserRelationshipPort.loadPairForUpdate(new UserId(userId), new UserId(friendId)))
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
