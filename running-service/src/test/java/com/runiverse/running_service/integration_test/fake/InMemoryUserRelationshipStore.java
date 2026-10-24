package com.runiverse.running_service.integration_test.fake;

import com.runiverse.running_service.application.relationship.port.out.EnsureUserRelationshipPairPort;
import com.runiverse.running_service.application.relationship.port.out.LoadUserRelationshipPort;
import com.runiverse.running_service.application.relationship.port.out.SaveUserRelationshipPort;
import com.runiverse.running_service.application.relationship.port.out.UserRelationshipPair;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.relationship.UserRelationship;
import com.runiverse.running_service.domain.relationship.vo.RelationshipType;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

// 페이크 조립은 한 스레드라 잠금을 흉내 내지 않는다 — 실제 잠금은 E2E에서만 확인된다
public class InMemoryUserRelationshipStore implements LoadUserRelationshipPort, SaveUserRelationshipPort,
        EnsureUserRelationshipPairPort {

    // user_relationships의 한 행 — 꺼낼 때마다 새 도메인 객체를 만들어 저장 전 변경이 새지 않게 한다
    private record Row(long id, UUID userId, UUID targetId, RelationshipType type) {
    }

    private record Key(UUID userId, UUID targetId) {
    }

    private final Map<Key, Row> rows = new LinkedHashMap<>();
    private long nextId = 1;

    @Override
    public UserRelationshipPair loadPairForUpdate(UserId userId, UserId targetId) {
        return new UserRelationshipPair(
                find(userId.value(), targetId.value()).orElse(null),
                find(targetId.value(), userId.value()).orElse(null));
    }

    // ON CONFLICT DO NOTHING과 같다 — 있는 행은 그대로 두고 없는 쪽만 NONE으로 만든다
    @Override
    public void ensurePair(UserId userId, UserId targetId) {
        insertNoneIfAbsent(userId.value(), targetId.value());
        insertNoneIfAbsent(targetId.value(), userId.value());
    }

    @Override
    public void save(UserRelationship relationship) {
        UUID userId = relationship.getUserId().value();
        UUID targetId = relationship.getTargetId().value();
        long id = relationship.getUserRelationshipId()
                .map(relationshipId -> relationshipId.value())
                .orElseGet(() -> nextId++);
        rows.put(new Key(userId, targetId), new Row(id, userId, targetId, relationship.getType()));
    }

    // 요청 API 없이 "requester가 receiver에게 요청한" 상태를 만든다
    public void seedRequest(UUID requesterId, UUID receiverId) {
        UserRelationship sent = UserRelationship.none(requesterId, receiverId);
        sent.sendRequest();
        UserRelationship received = UserRelationship.none(receiverId, requesterId);
        received.receiveRequest();
        save(sent);
        save(received);
    }

    // 요청·수락 API 없이 두 사람이 친구인 상태를 만든다
    public void seedFriend(UUID userId, UUID targetId) {
        UserRelationship mine = UserRelationship.none(userId, targetId);
        mine.sendRequest();
        mine.accept();
        UserRelationship theirs = UserRelationship.none(targetId, userId);
        theirs.receiveRequest();
        theirs.accept();
        save(mine);
        save(theirs);
    }

    public Optional<RelationshipType> typeOf(UUID userId, UUID targetId) {
        return Optional.ofNullable(rows.get(new Key(userId, targetId))).map(Row::type);
    }

    private void insertNoneIfAbsent(UUID userId, UUID targetId) {
        rows.computeIfAbsent(new Key(userId, targetId),
                key -> new Row(nextId++, userId, targetId, RelationshipType.NONE));
    }

    private Optional<UserRelationship> find(UUID userId, UUID targetId) {
        return Optional.ofNullable(rows.get(new Key(userId, targetId)))
                .map(row -> UserRelationship.builder()
                        .userRelationshipId(row.id())
                        .userId(row.userId())
                        .targetId(row.targetId())
                        .type(row.type())
                        .build());
    }
}
