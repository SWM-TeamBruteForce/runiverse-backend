package com.runiverse.running_service.integration_test.relationship;

import com.github.f4b6a3.uuid.UuidCreator;
import com.runiverse.running_service.application.relationship.command.accept.AcceptFriendRequestCommand;
import com.runiverse.running_service.application.relationship.command.accept.AcceptFriendRequestHandler;
import com.runiverse.running_service.application.relationship.command.accept.AcceptFriendRequestResult;
import com.runiverse.running_service.application.relationship.common.FriendRequestAcceptor;
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

@DisplayName("친구 요청 수락 통합 테스트")
public class AcceptFriendRequestIntegrationTest extends IntegrationTestSupport {

    private InMemoryUserRelationshipStore relationshipStore;
    private AcceptFriendRequestHandler acceptFriendRequestHandler;

    private UUID requesterId;
    private UUID receiverId;

    @BeforeEach
    void setUp() {
        relationshipStore = new InMemoryUserRelationshipStore();
        acceptFriendRequestHandler = new AcceptFriendRequestHandler(
                relationshipStore,                              // LoadUserRelationshipPort
                new FriendRequestAcceptor(
                        relationshipStore,                      // SaveUserRelationshipPort
                        (userId, payload) -> { })               // SendUserNotificationPort — 알림 저장은 이 테스트의 관심사가 아니다
        );
        requesterId = UuidCreator.getTimeOrderedEpoch();
        receiverId = UuidCreator.getTimeOrderedEpoch();
    }

    @Test
    @DisplayName("받은 요청을 수락하면 양쪽 행이 모두 친구가 된다")
    void acceptMakesBothSidesFriends() {
        // given
        relationshipStore.seedRequest(requesterId, receiverId);

        // when
        AcceptFriendRequestResult result = acceptFriendRequestHandler.handle(
                new AcceptFriendRequestCommand(receiverId, requesterId));

        // then
        assertThat(result.relationshipType()).isEqualTo(RelationshipType.FRIEND);
        assertThat(relationshipStore.typeOf(receiverId, requesterId)).hasValue(RelationshipType.FRIEND);
        assertThat(relationshipStore.typeOf(requesterId, receiverId)).hasValue(RelationshipType.FRIEND);
    }

    @Test
    @DisplayName("수락을 다시 보내도 에러 없이 같은 응답이고 관계는 그대로다")
    void acceptAgainIsIdempotent() {
        // given -> 첫 응답을 못 받은 앱이 다시 보낸다
        relationshipStore.seedRequest(requesterId, receiverId);
        acceptFriendRequestHandler.handle(new AcceptFriendRequestCommand(receiverId, requesterId));

        // when
        AcceptFriendRequestResult result = acceptFriendRequestHandler.handle(
                new AcceptFriendRequestCommand(receiverId, requesterId));

        // then
        assertThat(result.relationshipType()).isEqualTo(RelationshipType.FRIEND);
        assertThat(relationshipStore.typeOf(receiverId, requesterId)).hasValue(RelationshipType.FRIEND);
        assertThat(relationshipStore.typeOf(requesterId, receiverId)).hasValue(RelationshipType.FRIEND);
    }

    @Test
    @DisplayName("보낸 사람이 자기 요청을 수락하면 404이고 요청은 그대로 남는다")
    void requesterCannotAcceptOwnRequest() {
        // given
        relationshipStore.seedRequest(requesterId, receiverId);

        // when & then
        assertThatThrownBy(() -> acceptFriendRequestHandler.handle(
                new AcceptFriendRequestCommand(requesterId, receiverId)))
                .isInstanceOf(FriendRequestNotFoundException.class);
        assertThat(relationshipStore.typeOf(requesterId, receiverId)).hasValue(RelationshipType.FRIEND_REQUEST_SENT);
        assertThat(relationshipStore.typeOf(receiverId, requesterId)).hasValue(RelationshipType.FRIEND_REQUEST_RECEIVED);
    }

    @Test
    @DisplayName("요청한 적 없는 사이면 404이고 행을 만들지 않는다")
    void noRequestIsNotFound() {
        // when & then
        assertThatThrownBy(() -> acceptFriendRequestHandler.handle(
                new AcceptFriendRequestCommand(receiverId, requesterId)))
                .isInstanceOf(FriendRequestNotFoundException.class);
        assertThat(relationshipStore.typeOf(receiverId, requesterId)).isEmpty();
        assertThat(relationshipStore.typeOf(requesterId, receiverId)).isEmpty();
    }
}
