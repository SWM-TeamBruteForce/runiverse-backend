package com.runiverse.running_service.unit_test.relationship.application;

import com.github.f4b6a3.uuid.UuidCreator;
import com.runiverse.running_service.application.relationship.common.FriendRequestAcceptor;
import com.runiverse.running_service.application.relationship.port.out.SaveUserRelationshipPort;
import com.runiverse.running_service.domain.relationship.UserRelationship;
import com.runiverse.running_service.domain.relationship.exception.InvalidRelationshipTransitionException;
import com.runiverse.running_service.domain.relationship.vo.RelationshipType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@DisplayName("친구 요청 확정 단위 테스트")
class FriendRequestAcceptorTest {

    @Mock
    private SaveUserRelationshipPort saveUserRelationshipPort;

    @InjectMocks
    private FriendRequestAcceptor acceptor;

    private UUID userId;
    private UUID requesterId;

    @BeforeEach
    void setUp() {
        userId = UuidCreator.getTimeOrderedEpoch();
        requesterId = UuidCreator.getTimeOrderedEpoch();
    }

    @Test
    @DisplayName("받은 요청과 보낸 요청의 짝이면 두 행을 친구로 바꿔 저장한다")
    void acceptMatchedPair() {
        // given
        UserRelationship mine = relationship(1L, userId, requesterId, RelationshipType.FRIEND_REQUEST_RECEIVED);
        UserRelationship theirs = relationship(2L, requesterId, userId, RelationshipType.FRIEND_REQUEST_SENT);

        // when
        acceptor.accept(mine, theirs);

        // then
        assertThat(mine.getType()).isEqualTo(RelationshipType.FRIEND);
        assertThat(theirs.getType()).isEqualTo(RelationshipType.FRIEND);
        verify(saveUserRelationshipPort).save(mine);
        verify(saveUserRelationshipPort).save(theirs);
    }

    @Test
    @DisplayName("상대 행이 없으면 짝이 깨진 데이터라 바꾸지도 저장하지도 않는다")
    void missingCounterpartIsServerError() {
        // given
        UserRelationship mine = relationship(1L, userId, requesterId, RelationshipType.FRIEND_REQUEST_RECEIVED);

        // when & then
        assertThatThrownBy(() -> acceptor.accept(mine, null))
                .isInstanceOf(IllegalStateException.class);
        assertThat(mine.getType()).isEqualTo(RelationshipType.FRIEND_REQUEST_RECEIVED);
        verifyNoInteractions(saveUserRelationshipPort);
    }

    @ParameterizedTest
    @EnumSource(value = RelationshipType.class, names = {"NONE", "FRIEND_REQUEST_RECEIVED", "FRIEND", "BLOCKED"})
    @DisplayName("상대 행이 보낸 요청이 아니면 짝이 깨진 데이터라 바꾸지도 저장하지도 않는다")
    void mismatchedCounterpartIsServerError(RelationshipType theirType) {
        // given
        UserRelationship mine = relationship(1L, userId, requesterId, RelationshipType.FRIEND_REQUEST_RECEIVED);
        UserRelationship theirs = relationship(2L, requesterId, userId, theirType);

        // when & then
        assertThatThrownBy(() -> acceptor.accept(mine, theirs))
                .isInstanceOf(IllegalStateException.class);
        assertThat(mine.getType()).isEqualTo(RelationshipType.FRIEND_REQUEST_RECEIVED);
        assertThat(theirs.getType()).isEqualTo(theirType);
        verifyNoInteractions(saveUserRelationshipPort);
    }

    @Test
    @DisplayName("내 행이 받은 요청이 아니면 도메인 전이 규칙이 막아 저장하지 않는다")
    void notReceivedIsRejectedByDomain() {
        // given -> 호출하는 쪽이 확인을 빠뜨린 경우다. NONE에서 바로 FRIEND로 가지 않는다
        UserRelationship mine = relationship(1L, userId, requesterId, RelationshipType.NONE);
        UserRelationship theirs = relationship(2L, requesterId, userId, RelationshipType.FRIEND_REQUEST_SENT);

        // when & then
        assertThatThrownBy(() -> acceptor.accept(mine, theirs))
                .isInstanceOf(InvalidRelationshipTransitionException.class);
        verifyNoInteractions(saveUserRelationshipPort);
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
