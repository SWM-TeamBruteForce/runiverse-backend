package com.runiverse.running_service.integration_test.relationship;

import com.github.f4b6a3.uuid.UuidCreator;
import com.runiverse.running_service.application.auth.command.signup.SignUpCommand;
import com.runiverse.running_service.application.auth.command.signup.SignUpHandler;
import com.runiverse.running_service.application.relationship.command.accept.AcceptFriendRequestCommand;
import com.runiverse.running_service.application.relationship.command.accept.AcceptFriendRequestHandler;
import com.runiverse.running_service.application.relationship.command.request.RequestFriendCommand;
import com.runiverse.running_service.application.relationship.command.request.RequestFriendHandler;
import com.runiverse.running_service.application.relationship.command.request.RequestFriendResult;
import com.runiverse.running_service.application.relationship.common.FriendRequestAcceptor;
import com.runiverse.running_service.application.relationship.exception.CannotFriendSelfException;
import com.runiverse.running_service.application.relationship.exception.FriendRequestAlreadyExistsException;
import com.runiverse.running_service.application.relationship.exception.FriendTargetNotFoundException;
import com.runiverse.running_service.domain.relationship.vo.RelationshipType;
import com.runiverse.running_service.integration_test.IntegrationTestSupport;
import com.runiverse.running_service.integration_test.fake.InMemoryUserRelationshipStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("친구 요청 통합 테스트")
public class RequestFriendIntegrationTest extends IntegrationTestSupport {

    private static final String PASSWORD = "Password123!";

    private InMemoryUserRelationshipStore relationshipStore;
    private SignUpHandler signUpHandler;
    private RequestFriendHandler requestFriendHandler;
    private AcceptFriendRequestHandler acceptFriendRequestHandler;

    private UUID requesterId;
    private UUID receiverId;

    @BeforeEach
    void setUp() {
        relationshipStore = new InMemoryUserRelationshipStore();
        signUpHandler = newSignUpHandler();
        FriendRequestAcceptor acceptor = new FriendRequestAcceptor(relationshipStore);  // SaveUserRelationshipPort
        requestFriendHandler = new RequestFriendHandler(
                userStore,          // LoadUserByIdPort
                relationshipStore,  // EnsureUserRelationshipPairPort
                relationshipStore,  // LoadUserRelationshipPort
                relationshipStore,  // SaveUserRelationshipPort
                acceptor
        );
        acceptFriendRequestHandler = new AcceptFriendRequestHandler(
                relationshipStore,  // LoadUserRelationshipPort
                acceptor
        );
        requesterId = signUp("requester@runiverse.com");
        receiverId = signUp("receiver@runiverse.com");
    }

    private UUID signUp(String email) {
        return signUpHandler.handle(new SignUpCommand(issueVerificationTicket(email), PASSWORD)).userId();
    }

    @Test
    @DisplayName("처음 요청하면 보낸 쪽은 보낸 요청, 받은 쪽은 받은 요청이 된다")
    void requestCreatesPendingPair() {
        // when
        RequestFriendResult result = requestFriendHandler.handle(new RequestFriendCommand(requesterId, receiverId));

        // then
        assertThat(result.relationshipType()).isEqualTo(RelationshipType.FRIEND_REQUEST_SENT);
        assertThat(relationshipStore.typeOf(requesterId, receiverId)).hasValue(RelationshipType.FRIEND_REQUEST_SENT);
        assertThat(relationshipStore.typeOf(receiverId, requesterId)).hasValue(RelationshipType.FRIEND_REQUEST_RECEIVED);
    }

    @Test
    @DisplayName("요청을 받은 쪽이 수락하면 두 사람은 친구가 된다")
    void requestThenAcceptBecomesFriend() {
        // given
        requestFriendHandler.handle(new RequestFriendCommand(requesterId, receiverId));

        // when
        acceptFriendRequestHandler.handle(new AcceptFriendRequestCommand(receiverId, requesterId));

        // then
        assertThat(relationshipStore.typeOf(requesterId, receiverId)).hasValue(RelationshipType.FRIEND);
        assertThat(relationshipStore.typeOf(receiverId, requesterId)).hasValue(RelationshipType.FRIEND);
    }

    @Test
    @DisplayName("요청을 받은 쪽이 거꾸로 요청하면 수락 없이 바로 친구가 된다")
    void mutualRequestBecomesFriend() {
        // given
        requestFriendHandler.handle(new RequestFriendCommand(requesterId, receiverId));

        // when
        RequestFriendResult result = requestFriendHandler.handle(new RequestFriendCommand(receiverId, requesterId));

        // then
        assertThat(result.relationshipType()).isEqualTo(RelationshipType.FRIEND);
        assertThat(relationshipStore.typeOf(requesterId, receiverId)).hasValue(RelationshipType.FRIEND);
        assertThat(relationshipStore.typeOf(receiverId, requesterId)).hasValue(RelationshipType.FRIEND);
    }

    @Test
    @DisplayName("같은 사람에게 다시 요청하면 409이고 요청은 그대로다")
    void duplicateRequestIsConflict() {
        // given
        requestFriendHandler.handle(new RequestFriendCommand(requesterId, receiverId));

        // when & then
        assertThatThrownBy(() -> requestFriendHandler.handle(new RequestFriendCommand(requesterId, receiverId)))
                .isInstanceOf(FriendRequestAlreadyExistsException.class);
        assertThat(relationshipStore.typeOf(requesterId, receiverId)).hasValue(RelationshipType.FRIEND_REQUEST_SENT);
        assertThat(relationshipStore.typeOf(receiverId, requesterId)).hasValue(RelationshipType.FRIEND_REQUEST_RECEIVED);
    }

    @Test
    @DisplayName("이미 친구인 사람에게 요청하면 409다")
    void requestToFriendIsConflict() {
        // given
        requestFriendHandler.handle(new RequestFriendCommand(requesterId, receiverId));
        acceptFriendRequestHandler.handle(new AcceptFriendRequestCommand(receiverId, requesterId));

        // when & then -> 성립한 뒤로는 방향에 의미가 없으니 양쪽 다 막힌다
        assertThatThrownBy(() -> requestFriendHandler.handle(new RequestFriendCommand(requesterId, receiverId)))
                .isInstanceOf(FriendRequestAlreadyExistsException.class);
        assertThatThrownBy(() -> requestFriendHandler.handle(new RequestFriendCommand(receiverId, requesterId)))
                .isInstanceOf(FriendRequestAlreadyExistsException.class);
    }

    @Test
    @DisplayName("가입하지 않은 사람에게 요청하면 404이고 관계 행을 만들지 않는다")
    void requestToUnknownUserIsNotFound() {
        // given
        UUID unknownId = UuidCreator.getTimeOrderedEpoch();

        // when & then
        assertThatThrownBy(() -> requestFriendHandler.handle(new RequestFriendCommand(requesterId, unknownId)))
                .isInstanceOf(FriendTargetNotFoundException.class);
        assertThat(relationshipStore.typeOf(requesterId, unknownId)).isEmpty();
    }

    @Test
    @DisplayName("자기 자신에게 요청하면 400이고 관계 행을 만들지 않는다")
    void requestToSelfIsRejected() {
        // when & then
        assertThatThrownBy(() -> requestFriendHandler.handle(new RequestFriendCommand(requesterId, requesterId)))
                .isInstanceOf(CannotFriendSelfException.class);
        assertThat(relationshipStore.typeOf(requesterId, requesterId)).isEmpty();
    }
}
