package com.runiverse.running_service.integration_test.relationship;

import com.github.f4b6a3.uuid.UuidCreator;
import com.runiverse.running_service.application.relationship.command.cancel.CancelFriendRequestCommand;
import com.runiverse.running_service.application.relationship.command.cancel.CancelFriendRequestHandler;
import com.runiverse.running_service.application.relationship.common.RelationshipEnder;
import com.runiverse.running_service.application.relationship.exception.FriendRequestNotFoundException;
import com.runiverse.running_service.domain.relationship.vo.RelationshipType;
import com.runiverse.running_service.integration_test.IntegrationTestSupport;
import com.runiverse.running_service.integration_test.fake.InMemoryUserRelationshipStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("친구 요청 취소·거절 통합 테스트")
public class CancelFriendRequestIntegrationTest extends IntegrationTestSupport {

    private InMemoryUserRelationshipStore relationshipStore;
    private CancelFriendRequestHandler cancelFriendRequestHandler;

    private UUID requesterId;
    private UUID receiverId;

    @BeforeEach
    void setUp() {
        relationshipStore = new InMemoryUserRelationshipStore();
        cancelFriendRequestHandler = new CancelFriendRequestHandler(
                relationshipStore,                          // LoadUserRelationshipPort
                new RelationshipEnder(relationshipStore)    // SaveUserRelationshipPort
        );
        requesterId = UuidCreator.getTimeOrderedEpoch();
        receiverId = UuidCreator.getTimeOrderedEpoch();
    }

    @Test
    @DisplayName("보낸 사람이 취소하면 양쪽 행이 NONE으로 남는다")
    void requesterCancels() {
        // given
        relationshipStore.seedRequest(requesterId, receiverId);

        // when
        cancelFriendRequestHandler.handle(new CancelFriendRequestCommand(requesterId, receiverId));

        // then -> 행을 지우지 않는다
        assertThat(relationshipStore.typeOf(requesterId, receiverId)).hasValue(RelationshipType.NONE);
        assertThat(relationshipStore.typeOf(receiverId, requesterId)).hasValue(RelationshipType.NONE);
    }

    @Test
    @DisplayName("받은 사람이 거절하면 양쪽 행이 NONE으로 남는다")
    void receiverRejects() {
        // given
        relationshipStore.seedRequest(requesterId, receiverId);

        // when
        cancelFriendRequestHandler.handle(new CancelFriendRequestCommand(receiverId, requesterId));

        // then
        assertThat(relationshipStore.typeOf(requesterId, receiverId)).hasValue(RelationshipType.NONE);
        assertThat(relationshipStore.typeOf(receiverId, requesterId)).hasValue(RelationshipType.NONE);
    }

    @Test
    @DisplayName("그새 친구가 됐어도 취소하면 양쪽 행이 NONE이 된다")
    void cancelAfterAcceptedEndsFriendship() {
        // given -> 보낸 사람의 화면은 아직 "요청 취소"인데 상대가 막 수락했다
        relationshipStore.seedFriend(requesterId, receiverId);

        // when
        cancelFriendRequestHandler.handle(new CancelFriendRequestCommand(requesterId, receiverId));

        // then
        assertThat(relationshipStore.typeOf(requesterId, receiverId)).hasValue(RelationshipType.NONE);
        assertThat(relationshipStore.typeOf(receiverId, requesterId)).hasValue(RelationshipType.NONE);
    }

    @Test
    @DisplayName("이미 취소된 요청을 다시 지우면 404이고 관계는 NONE 그대로다")
    void cancelAgainIsNotFound() {
        // given
        relationshipStore.seedRequest(requesterId, receiverId);
        cancelFriendRequestHandler.handle(new CancelFriendRequestCommand(requesterId, receiverId));

        // when & then -> 수락과 달리 멱등이 아니다
        assertThatThrownBy(() -> cancelFriendRequestHandler.handle(
                new CancelFriendRequestCommand(requesterId, receiverId)))
                .isInstanceOf(FriendRequestNotFoundException.class);
        assertThat(relationshipStore.typeOf(requesterId, receiverId)).hasValue(RelationshipType.NONE);
        assertThat(relationshipStore.typeOf(receiverId, requesterId)).hasValue(RelationshipType.NONE);
    }

    @Test
    @DisplayName("요청한 적 없는 사이면 404이고 행을 만들지 않는다")
    void noRequestIsNotFound() {
        // when & then
        assertThatThrownBy(() -> cancelFriendRequestHandler.handle(
                new CancelFriendRequestCommand(requesterId, receiverId)))
                .isInstanceOf(FriendRequestNotFoundException.class);
        assertThat(relationshipStore.typeOf(requesterId, receiverId)).isEmpty();
        assertThat(relationshipStore.typeOf(receiverId, requesterId)).isEmpty();
    }
}
