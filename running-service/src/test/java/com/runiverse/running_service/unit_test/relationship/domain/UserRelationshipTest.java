package com.runiverse.running_service.unit_test.relationship.domain;

import com.github.f4b6a3.uuid.UuidCreator;
import com.runiverse.running_service.domain.common.exception.UserIdRequiredException;
import com.runiverse.running_service.domain.relationship.UserRelationship;
import com.runiverse.running_service.domain.relationship.exception.InvalidRelationshipTransitionException;
import com.runiverse.running_service.domain.relationship.exception.InvalidUserRelationshipIdException;
import com.runiverse.running_service.domain.relationship.exception.RelationshipTypeRequiredException;
import com.runiverse.running_service.domain.relationship.exception.SelfRelationshipException;
import com.runiverse.running_service.domain.relationship.vo.RelationshipType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class UserRelationshipTest {

    private static final UUID ME = UuidCreator.getTimeOrderedEpoch();
    private static final UUID TARGET = UuidCreator.getTimeOrderedEpoch();

    // 저장돼 있던 관계 — 각 테스트는 type만 바꿔 복원한다
    private static UserRelationship stored(RelationshipType type) {
        return UserRelationship.builder()
                .userRelationshipId(1L)
                .userId(ME)
                .targetId(TARGET)
                .type(type)
                .build();
    }

    @Nested
    @DisplayName("생성 테스트")
    class CreateTest {

        @Test
        @DisplayName("처음 맺는 쌍은 관계 없음으로 시작하고 식별자가 없다")
        void noneStartsWithoutRelationship() {
            // when
            UserRelationship relationship = UserRelationship.none(ME, TARGET);

            // then
            assertThat(relationship.getType()).isEqualTo(RelationshipType.NONE);
            assertThat(relationship.getUserId().value()).isEqualTo(ME);
            assertThat(relationship.getTargetId().value()).isEqualTo(TARGET);
            assertThat(relationship.isNew()).isTrue();
            assertThat(relationship.getUserRelationshipId()).isEmpty();
        }

        @Test
        @DisplayName("저장된 관계는 식별자를 갖는다")
        void storedRelationshipHasId() {
            // when
            UserRelationship relationship = stored(RelationshipType.FRIEND);

            // then
            assertThat(relationship.isNew()).isFalse();
            assertThat(relationship.getUserRelationshipId())
                    .hasValueSatisfying(id -> assertThat(id.value()).isEqualTo(1L));
        }

        @Test
        @DisplayName("자기 자신과의 관계는 만들 수 없다")
        void selfRelationshipFails() {
            // when & then
            assertThatThrownBy(() -> UserRelationship.none(ME, ME))
                    .isInstanceOf(SelfRelationshipException.class);
        }

        @Test
        @DisplayName("관계 종류 없이 복원할 수 없다")
        void typeRequired() {
            // when & then
            assertThatThrownBy(() -> stored(null))
                    .isInstanceOf(RelationshipTypeRequiredException.class);
        }

        @Test
        @DisplayName("상대 없이 만들 수 없다")
        void targetRequired() {
            // when & then
            assertThatThrownBy(() -> UserRelationship.none(ME, null))
                    .isInstanceOf(UserIdRequiredException.class);
        }

        @Test
        @DisplayName("1보다 작은 식별자로 복원할 수 없다")
        void invalidIdFails() {
            // when & then
            assertThatThrownBy(() -> UserRelationship.builder()
                    .userRelationshipId(0L)
                    .userId(ME)
                    .targetId(TARGET)
                    .type(RelationshipType.NONE)
                    .build())
                    .isInstanceOf(InvalidUserRelationshipIdException.class);
        }
    }

    @Nested
    @DisplayName("친구 요청·수락 테스트")
    class FriendRequestTest {

        @Test
        @DisplayName("요청을 보내면 보낸 요청, 받으면 받은 요청이 된다")
        void requestSetsDirection() {
            // given
            UserRelationship sender = UserRelationship.none(ME, TARGET);
            UserRelationship receiver = UserRelationship.none(TARGET, ME);

            // when
            sender.sendRequest();
            receiver.receiveRequest();

            // then
            assertThat(sender.getType()).isEqualTo(RelationshipType.FRIEND_REQUEST_SENT);
            assertThat(receiver.getType()).isEqualTo(RelationshipType.FRIEND_REQUEST_RECEIVED);
        }

        @Test
        @DisplayName("끝난 관계(NONE)로 남은 행에서 다시 요청할 수 있다")
        void requestAgainFromEndedRelationship() {
            // given
            UserRelationship relationship = stored(RelationshipType.NONE);

            // when
            relationship.sendRequest();

            // then
            assertThat(relationship.getType()).isEqualTo(RelationshipType.FRIEND_REQUEST_SENT);
            assertThat(relationship.isNew()).isFalse();
        }

        @ParameterizedTest
        @EnumSource(value = RelationshipType.class,
                names = {"FRIEND_REQUEST_SENT", "FRIEND_REQUEST_RECEIVED"})
        @DisplayName("보낸 쪽·받은 쪽 행 모두 수락하면 친구가 된다")
        void acceptBecomesFriend(RelationshipType request) {
            // given
            UserRelationship relationship = stored(request);

            // when
            relationship.accept();

            // then
            assertThat(relationship.getType()).isEqualTo(RelationshipType.FRIEND);
        }

        @ParameterizedTest
        @EnumSource(value = RelationshipType.class,
                names = {"FRIEND_REQUEST_SENT", "FRIEND_REQUEST_RECEIVED"},
                mode = EnumSource.Mode.EXCLUDE)
        @DisplayName("요청 중이 아니면 수락할 수 없다")
        void acceptWithoutRequestFails(RelationshipType type) {
            // given
            UserRelationship relationship = stored(type);

            // when & then
            assertThatThrownBy(relationship::accept)
                    .isInstanceOf(InvalidRelationshipTransitionException.class);
        }

        @Test
        @DisplayName("차단한 상대에게는 요청을 보낼 수 없다")
        void requestWhileBlockedFails() {
            // given
            UserRelationship relationship = stored(RelationshipType.BLOCKED);

            // when & then
            assertThatThrownBy(relationship::sendRequest)
                    .isInstanceOf(InvalidRelationshipTransitionException.class);
        }
    }

    @Nested
    @DisplayName("관계 종료 테스트")
    class EndTest {

        @ParameterizedTest
        @EnumSource(value = RelationshipType.class,
                names = {"FRIEND", "FRIEND_REQUEST_SENT", "FRIEND_REQUEST_RECEIVED"})
        @DisplayName("친구 삭제·요청 취소·거절은 관계 없음으로 되돌린다")
        void endReturnsToNone(RelationshipType type) {
            // given
            UserRelationship relationship = stored(type);

            // when
            relationship.end();

            // then -> 행은 지우지 않고 같은 행을 NONE으로 남긴다
            assertThat(relationship.getType()).isEqualTo(RelationshipType.NONE);
            assertThat(relationship.isNew()).isFalse();
        }

        @Test
        @DisplayName("차단은 관계 종료로 풀 수 없다")
        void endWhileBlockedFails() {
            // given
            UserRelationship relationship = stored(RelationshipType.BLOCKED);

            // when & then -> 차단은 해제로만 푼다
            assertThatThrownBy(relationship::end)
                    .isInstanceOf(InvalidRelationshipTransitionException.class);
            assertThat(relationship.getType()).isEqualTo(RelationshipType.BLOCKED);
        }

        @Test
        @DisplayName("이미 관계가 없으면 종료할 수 없다")
        void endWhenNoneFails() {
            // given
            UserRelationship relationship = stored(RelationshipType.NONE);

            // when & then
            assertThatThrownBy(relationship::end)
                    .isInstanceOf(InvalidRelationshipTransitionException.class);
        }
    }

    @Nested
    @DisplayName("차단 테스트")
    class BlockTest {

        @ParameterizedTest
        @EnumSource(value = RelationshipType.class, names = "BLOCKED", mode = EnumSource.Mode.EXCLUDE)
        @DisplayName("어떤 관계였든 차단이 덮어쓴다")
        void blockOverridesRelationship(RelationshipType type) {
            // given
            UserRelationship relationship = stored(type);

            // when
            relationship.block();

            // then -> 친구였어도 차단과 친구가 함께 남지 않는다
            assertThat(relationship.getType()).isEqualTo(RelationshipType.BLOCKED);
        }

        @Test
        @DisplayName("차단을 해제하면 친구로 돌아가지 않고 관계 없음이 된다")
        void unblockReturnsToNone() {
            // given
            UserRelationship relationship = stored(RelationshipType.BLOCKED);

            // when
            relationship.unblock();

            // then
            assertThat(relationship.getType()).isEqualTo(RelationshipType.NONE);
        }

        @ParameterizedTest
        @EnumSource(value = RelationshipType.class, names = "BLOCKED", mode = EnumSource.Mode.EXCLUDE)
        @DisplayName("차단하지 않은 관계는 해제할 수 없다")
        void unblockWithoutBlockFails(RelationshipType type) {
            // given
            UserRelationship relationship = stored(type);

            // when & then -> 친구 관계를 해제 경로로 끝내지 못하게 한다
            assertThatThrownBy(relationship::unblock)
                    .isInstanceOf(InvalidRelationshipTransitionException.class);
            assertThat(relationship.getType()).isEqualTo(type);
        }
    }
}
