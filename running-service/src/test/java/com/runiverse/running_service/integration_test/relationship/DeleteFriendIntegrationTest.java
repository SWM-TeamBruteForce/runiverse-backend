package com.runiverse.running_service.integration_test.relationship;

import com.github.f4b6a3.uuid.UuidCreator;
import com.runiverse.running_service.application.relationship.command.delete.DeleteFriendCommand;
import com.runiverse.running_service.application.relationship.command.delete.DeleteFriendHandler;
import com.runiverse.running_service.application.relationship.common.RelationshipEnder;
import com.runiverse.running_service.application.relationship.exception.FriendNotFoundException;
import com.runiverse.running_service.domain.relationship.vo.RelationshipType;
import com.runiverse.running_service.integration_test.IntegrationTestSupport;
import com.runiverse.running_service.integration_test.fake.InMemoryUserRelationshipStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("친구 삭제 통합 테스트")
public class DeleteFriendIntegrationTest extends IntegrationTestSupport {

    private InMemoryUserRelationshipStore relationshipStore;
    private DeleteFriendHandler deleteFriendHandler;

    private UUID requesterId;
    private UUID receiverId;

    @BeforeEach
    void setUp() {
        relationshipStore = new InMemoryUserRelationshipStore();
        deleteFriendHandler = new DeleteFriendHandler(
                relationshipStore,                          // LoadUserRelationshipPort
                new RelationshipEnder(relationshipStore)    // SaveUserRelationshipPort
        );
        requesterId = UuidCreator.getTimeOrderedEpoch();
        receiverId = UuidCreator.getTimeOrderedEpoch();
    }

    @Test
    @DisplayName("요청했던 쪽이 삭제하면 양쪽 행이 NONE으로 남는다")
    void requesterDeletes() {
        // given
        relationshipStore.seedFriend(requesterId, receiverId);

        // when
        deleteFriendHandler.handle(new DeleteFriendCommand(requesterId, receiverId));

        // then -> 행을 지우지 않는다
        assertThat(relationshipStore.typeOf(requesterId, receiverId)).hasValue(RelationshipType.NONE);
        assertThat(relationshipStore.typeOf(receiverId, requesterId)).hasValue(RelationshipType.NONE);
    }

    @Test
    @DisplayName("수락했던 쪽이 삭제해도 양쪽 행이 NONE으로 남는다")
    void receiverDeletes() {
        // given -> 성립한 뒤로는 방향에 의미가 없다
        relationshipStore.seedFriend(requesterId, receiverId);

        // when
        deleteFriendHandler.handle(new DeleteFriendCommand(receiverId, requesterId));

        // then
        assertThat(relationshipStore.typeOf(requesterId, receiverId)).hasValue(RelationshipType.NONE);
        assertThat(relationshipStore.typeOf(receiverId, requesterId)).hasValue(RelationshipType.NONE);
    }

    @Test
    @DisplayName("이미 삭제한 친구를 다시 지우면 404이고 관계는 NONE 그대로다")
    void deleteAgainIsNotFound() {
        // given
        relationshipStore.seedFriend(requesterId, receiverId);
        deleteFriendHandler.handle(new DeleteFriendCommand(requesterId, receiverId));

        // when & then
        assertThatThrownBy(() -> deleteFriendHandler.handle(new DeleteFriendCommand(receiverId, requesterId)))
                .isInstanceOf(FriendNotFoundException.class);
        assertThat(relationshipStore.typeOf(requesterId, receiverId)).hasValue(RelationshipType.NONE);
        assertThat(relationshipStore.typeOf(receiverId, requesterId)).hasValue(RelationshipType.NONE);
    }

    @Test
    @DisplayName("요청 중인 관계는 이 경로로 지우지 않는다 — 404이고 요청은 그대로 남는다")
    void pendingRequestIsNotFound() {
        // given
        relationshipStore.seedRequest(requesterId, receiverId);

        // when & then
        assertThatThrownBy(() -> deleteFriendHandler.handle(new DeleteFriendCommand(requesterId, receiverId)))
                .isInstanceOf(FriendNotFoundException.class);
        assertThat(relationshipStore.typeOf(requesterId, receiverId)).hasValue(RelationshipType.FRIEND_REQUEST_SENT);
        assertThat(relationshipStore.typeOf(receiverId, requesterId)).hasValue(RelationshipType.FRIEND_REQUEST_RECEIVED);
    }

    @Test
    @DisplayName("관계가 없는 사이면 404이고 행을 만들지 않는다")
    void noRelationshipIsNotFound() {
        // when & then
        assertThatThrownBy(() -> deleteFriendHandler.handle(new DeleteFriendCommand(requesterId, receiverId)))
                .isInstanceOf(FriendNotFoundException.class);
        assertThat(relationshipStore.typeOf(requesterId, receiverId)).isEmpty();
        assertThat(relationshipStore.typeOf(receiverId, requesterId)).isEmpty();
    }
}
