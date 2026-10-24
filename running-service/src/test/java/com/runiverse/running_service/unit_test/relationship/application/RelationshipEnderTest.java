package com.runiverse.running_service.unit_test.relationship.application;

import com.github.f4b6a3.uuid.UuidCreator;
import com.runiverse.running_service.application.relationship.common.RelationshipEnder;
import com.runiverse.running_service.application.relationship.port.out.SaveUserRelationshipPort;
import com.runiverse.running_service.domain.relationship.UserRelationship;
import com.runiverse.running_service.domain.relationship.vo.RelationshipType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@DisplayName("관계 끊기 공용 컴포넌트 단위 테스트")
public class RelationshipEnderTest {

    @Mock
    private SaveUserRelationshipPort saveUserRelationshipPort;

    private RelationshipEnder relationshipEnder;

    private UUID userId;
    private UUID targetId;

    @BeforeEach
    void setUp() {
        relationshipEnder = new RelationshipEnder(saveUserRelationshipPort);
        userId = UuidCreator.getTimeOrderedEpoch();
        targetId = UuidCreator.getTimeOrderedEpoch();
    }

    @ParameterizedTest
    @MethodSource("matchedPairs")
    @DisplayName("짝이 맞으면 두 행을 모두 NONE으로 바꿔 저장한다")
    void endsMatchedPair(RelationshipType myType, RelationshipType theirType) {
        // given
        UserRelationship mine = relationship(1L, userId, targetId, myType);
        UserRelationship theirs = relationship(2L, targetId, userId, theirType);

        // when
        relationshipEnder.end(mine, theirs);

        // then
        assertThat(mine.getType()).isEqualTo(RelationshipType.NONE);
        assertThat(theirs.getType()).isEqualTo(RelationshipType.NONE);
        verify(saveUserRelationshipPort).save(mine);
        verify(saveUserRelationshipPort).save(theirs);
    }

    @ParameterizedTest
    @EnumSource(value = RelationshipType.class, names = {"FRIEND_REQUEST_SENT", "FRIEND_REQUEST_RECEIVED", "FRIEND"})
    @DisplayName("상대 행이 없으면 짝이 깨진 데이터라 아무것도 바꾸지 않고 실패한다")
    void missingCounterpartIsServerError(RelationshipType myType) {
        // given
        UserRelationship mine = relationship(1L, userId, targetId, myType);

        // when & then
        assertThatThrownBy(() -> relationshipEnder.end(mine, null))
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

        // when & then
        assertThatThrownBy(() -> relationshipEnder.end(mine, theirs))
                .isInstanceOf(IllegalStateException.class);
        assertThat(mine.getType()).isEqualTo(myType);
        assertThat(theirs.getType()).isEqualTo(theirType);
        verifyNoInteractions(saveUserRelationshipPort);
    }

    @ParameterizedTest
    @EnumSource(value = RelationshipType.class, names = {"NONE", "BLOCKED"})
    @DisplayName("내 행이 끊을 관계가 아니면 호출한 쪽의 버그라 아무것도 바꾸지 않고 실패한다")
    void notEndableIsServerError(RelationshipType myType) {
        // given -> 이 상태는 유스케이스가 404로 먼저 걸러야 한다
        UserRelationship mine = relationship(1L, userId, targetId, myType);
        UserRelationship theirs = relationship(2L, targetId, userId, myType);

        // when & then
        assertThatThrownBy(() -> relationshipEnder.end(mine, theirs))
                .isInstanceOf(IllegalStateException.class);
        assertThat(mine.getType()).isEqualTo(myType);
        verifyNoInteractions(saveUserRelationshipPort);
    }

    private static Stream<Arguments> matchedPairs() {
        return Stream.of(
                Arguments.of(RelationshipType.FRIEND_REQUEST_SENT, RelationshipType.FRIEND_REQUEST_RECEIVED),
                Arguments.of(RelationshipType.FRIEND_REQUEST_RECEIVED, RelationshipType.FRIEND_REQUEST_SENT),
                Arguments.of(RelationshipType.FRIEND, RelationshipType.FRIEND)
        );
    }

    // 같은 방향 요청끼리, 요청과 친구, 요청·친구와 끝난 관계처럼 짝이 될 수 없는 조합
    private static Stream<Arguments> mismatchedPairs() {
        return Stream.of(
                Arguments.of(RelationshipType.FRIEND_REQUEST_SENT, RelationshipType.FRIEND_REQUEST_SENT),
                Arguments.of(RelationshipType.FRIEND_REQUEST_SENT, RelationshipType.NONE),
                Arguments.of(RelationshipType.FRIEND_REQUEST_RECEIVED, RelationshipType.FRIEND_REQUEST_RECEIVED),
                Arguments.of(RelationshipType.FRIEND_REQUEST_RECEIVED, RelationshipType.FRIEND),
                Arguments.of(RelationshipType.FRIEND, RelationshipType.FRIEND_REQUEST_SENT),
                Arguments.of(RelationshipType.FRIEND, RelationshipType.NONE),
                Arguments.of(RelationshipType.FRIEND, RelationshipType.BLOCKED)
        );
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
