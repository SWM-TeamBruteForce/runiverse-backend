package com.runiverse.running_service.domain.relationship;

import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.relationship.exception.InvalidRelationshipTransitionException;
import com.runiverse.running_service.domain.relationship.exception.RelationshipTypeRequiredException;
import com.runiverse.running_service.domain.relationship.exception.SelfRelationshipException;
import com.runiverse.running_service.domain.relationship.vo.RelationshipType;
import com.runiverse.running_service.domain.relationship.vo.UserRelationshipId;
import lombok.Builder;
import lombok.Getter;

import java.util.Optional;
import java.util.UUID;

// 한 사람(userId)이 본 상대(targetId)와의 관계 — 친구·요청은 상대 쪽 행과 짝을 이루며, 짝은 애플리케이션이 함께 바꾼다
@Getter
public class UserRelationship {

    private final UserRelationshipId userRelationshipId;   // 저장 전에는 null
    private final UserId userId;                           // 행의 주인
    private final UserId targetId;                         // 상대
    private RelationshipType type;

    @Builder
    private UserRelationship(Long userRelationshipId, UUID userId, UUID targetId, RelationshipType type) {
        this.userRelationshipId = userRelationshipId == null ? null : new UserRelationshipId(userRelationshipId);
        this.userId = new UserId(userId);
        this.targetId = new UserId(targetId);

        if (this.userId.equals(this.targetId)) {
            throw new SelfRelationshipException();
        }
        if (type == null) {
            throw new RelationshipTypeRequiredException();
        }
        this.type = type;
    }

    // 처음 관계를 맺는 쌍 — 행이 없을 때 NONE으로 만들고 아래 행위로 상태를 바꾼다
    public static UserRelationship none(UUID userId, UUID targetId) {
        return builder()
                .userId(userId)
                .targetId(targetId)
                .type(RelationshipType.NONE)
                .build();
    }

    public void sendRequest() {
        this.type = type.transitionTo(RelationshipType.FRIEND_REQUEST_SENT);
    }

    public void receiveRequest() {
        this.type = type.transitionTo(RelationshipType.FRIEND_REQUEST_RECEIVED);
    }

    // 요청을 보낸 쪽·받은 쪽 행 모두 FRIEND가 된다
    public void accept() {
        this.type = type.transitionTo(RelationshipType.FRIEND);
    }

    // 요청 취소·거절·친구 삭제 — 차단은 이 경로로 풀지 않는다
    public void end() {
        if (type == RelationshipType.BLOCKED) {
            throw new InvalidRelationshipTransitionException();
        }
        this.type = type.transitionTo(RelationshipType.NONE);
    }

    // 친구·요청 중이었어도 차단이 덮어쓴다
    public void block() {
        this.type = type.transitionTo(RelationshipType.BLOCKED);
    }

    public void unblock() {
        if (type != RelationshipType.BLOCKED) {
            throw new InvalidRelationshipTransitionException();
        }
        this.type = type.transitionTo(RelationshipType.NONE);
    }

    public boolean isNew() {
        return userRelationshipId == null;
    }

    public Optional<UserRelationshipId> getUserRelationshipId() {
        return Optional.ofNullable(userRelationshipId);
    }
}
