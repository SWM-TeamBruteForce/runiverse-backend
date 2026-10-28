package com.runiverse.running_service.infrastructure.persistence.relationship;

import com.runiverse.running_service.application.common.port.out.CountFriendsPort;
import com.runiverse.running_service.application.common.port.out.LoadRelationshipTypesPort;
import com.runiverse.running_service.application.match.port.out.CheckFriendPort;
import com.runiverse.running_service.application.relationship.port.out.EnsureUserRelationshipPairPort;
import com.runiverse.running_service.application.relationship.port.out.FriendKeyset;
import com.runiverse.running_service.application.relationship.port.out.FriendRow;
import com.runiverse.running_service.application.relationship.port.out.LoadFriendsPort;
import com.runiverse.running_service.application.relationship.port.out.LoadUserRelationshipPort;
import com.runiverse.running_service.application.relationship.port.out.SaveUserRelationshipPort;
import com.runiverse.running_service.application.relationship.port.out.UserRelationshipPair;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.relationship.UserRelationship;
import com.runiverse.running_service.domain.relationship.vo.RelationshipType;
import com.runiverse.running_service.infrastructure.persistence.common.LikePatterns;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.TypedQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
@RequiredArgsConstructor
public class UserRelationshipPersistenceAdapter implements LoadUserRelationshipPort, SaveUserRelationshipPort,
        EnsureUserRelationshipPairPort, LoadFriendsPort, CheckFriendPort, LoadRelationshipTypesPort,
        CountFriendsPort {

    // JPQL에는 ON CONFLICT가 없어 네이티브로 쓴다. 이미 있는 행은 건드리지 않고, 다른 트랜잭션이 막 넣은 행이면
    // 그 커밋을 기다렸다 지나간다 — UNIQUE 위반이 나지 않는다
    private static final String INSERT_NONE_PAIR = """
            INSERT INTO user_relationships (user_id, target_id, type, created_at, updated_at)
            VALUES (:firstUserId, :firstTargetId, :type, :now, :now),
                   (:secondUserId, :secondTargetId, :type, :now, :now)
            ON CONFLICT (user_id, target_id) DO NOTHING
            """;
    // 상대의 닉네임·사진과 조회자가 그 사람과 맺은 관계를 함께 읽는다. 온보딩 전인 상대는 닉네임이 없어 JOIN에서 빠진다.
    // 본인 목록이면 조회자 행이 곧 r이라 v.type = r.type이다
    private static final String SELECT_FRIENDS = """
            SELECT NEW com.runiverse.running_service.application.relationship.port.out.FriendRow(
                r.targetId, o.nickname, u.profileImageKey, r.type, r.updatedAt, v.type)
            FROM UserRelationshipJpaEntity r
            JOIN UserOnboardingJpaEntity o ON o.userId = r.targetId
            JOIN UserJpaEntity u ON u.userId = r.targetId
            LEFT JOIN UserRelationshipJpaEntity v ON v.userId = :viewerId AND v.targetId = r.targetId
            WHERE r.userId = :ownerId
              AND r.type = :type
            """;
    private static final String NICKNAME_CONDITION = """
              AND o.nickname LIKE :nicknamePattern ESCAPE '\\'
            """;
    // 튜플 비교((a, b) < (x, y))를 JPQL이 받지 않아 풀어 쓴다
    private static final String AFTER_CONDITION = """
              AND (r.updatedAt < :afterUpdatedAt
                   OR (r.updatedAt = :afterUpdatedAt AND r.targetId < :afterUserId))
            """;
    // idx_user_relationship_list(user_id, type, updated_at, target_id)를 거꾸로 읽는다
    private static final String ORDER_FRIENDS = """
            ORDER BY r.updatedAt DESC, r.targetId DESC
            """;
    private final EntityManager entityManager;

    // 관계 행을 바꾸는 경로는 모두 이 메서드로 잠근다 — 따로 잠그는 경로가 생기면 잠금 순서가 엇갈려 교착에 빠진다.
    // 두 행을 한 쿼리로 id 순서대로 잠가 누가 호출하든 순서가 같다. users와 JOIN하면 그 행까지 잠기므로 JOIN하지 않는다
    @Override
    public UserRelationshipPair loadPairForUpdate(UserId userId, UserId targetId) {
        List<UserRelationshipJpaEntity> rows = entityManager.createQuery("""
                        SELECT r
                        FROM UserRelationshipJpaEntity r
                        WHERE (r.userId = :userId AND r.targetId = :targetId)
                           OR (r.userId = :targetId AND r.targetId = :userId)
                        ORDER BY r.userRelationshipId
                        """, UserRelationshipJpaEntity.class)
                .setParameter("userId", userId.value())
                .setParameter("targetId", targetId.value())
                // 상대가 커밋할 때까지 기다렸다 바뀐 행을 읽는다 — 취소된 요청을 수락하지 않게 한다
                .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                .getResultList();

        return new UserRelationshipPair(
                findOwnedBy(rows, userId.value()),
                findOwnedBy(rows, targetId.value()));
    }

    // 초대 대상이 친구인지 본다 — 친구는 대칭이라 내 행만 보면 된다.
    // 잠그지 않는다 — 보낸 뒤에 관계가 바뀌어도 초대는 이미 사건이라 되돌리지 않는다.
    // uk_user_relationship_user_target(user_id, target_id)를 탄다
    @Override
    public boolean isFriend(UserId userId, UserId targetId) {
        return entityManager.createQuery("""
                        SELECT COUNT(r)
                        FROM UserRelationshipJpaEntity r
                        WHERE r.userId = :userId
                          AND r.targetId = :targetId
                          AND r.type = :type
                        """, Long.class)
                .setParameter("userId", userId.value())
                .setParameter("targetId", targetId.value())
                .setParameter("type", RelationshipType.FRIEND)
                .getSingleResult() > 0;
    }

    // 프로필 요약의 친구 수 — 친구는 대칭이라 이 사람이 주인인 행만 센다.
    // idx_user_relationship_list(user_id, type, …)의 앞 두 컬럼으로 센다
    @Override
    public long countFriends(UserId userId) {
        return entityManager.createQuery("""
                        SELECT COUNT(r)
                        FROM UserRelationshipJpaEntity r
                        WHERE r.userId = :userId
                          AND r.type = :type
                        """, Long.class)
                .setParameter("userId", userId.value())
                .setParameter("type", RelationshipType.FRIEND)
                .getSingleResult();
    }

    @Override
    public void save(UserRelationship relationship) {
        if (relationship.isNew()) {
            entityManager.persist(UserRelationshipJpaEntity.create(
                    relationship.getUserId().value(),
                    relationship.getTargetId().value(),
                    relationship.getType()));
            return;
        }
        Long relationshipId = relationship.getUserRelationshipId().orElseThrow().value();
        // 잠금 조회로 이미 영속 상태라 다시 조회하지 않는다 — 더티 체킹이 UPDATE를 만든다
        UserRelationshipJpaEntity entity = entityManager.find(UserRelationshipJpaEntity.class, relationshipId);
        entity.changeType(relationship.getType());
    }

    private static UserRelationship findOwnedBy(List<UserRelationshipJpaEntity> rows, UUID ownerId) {
        return rows.stream()
                .filter(row -> row.getUserId().equals(ownerId))
                .findFirst()
                .map(UserRelationshipPersistenceAdapter::toDomain)
                .orElse(null);
    }

    @Override
    public void ensurePair(UserId userId, UserId targetId) {
        // 넣는 순서를 user_id로 고정한다 — A→B와 B→A가 반대 순서로 넣으면 서로의 행을 기다리다 교착에 빠진다
        List<UUID[]> rows = Stream.of(
                        new UUID[]{userId.value(), targetId.value()},
                        new UUID[]{targetId.value(), userId.value()})
                .sorted(Comparator.comparing(row -> row[0]))
                .toList();
        // 저장 시각은 JVM 타임존 기준이다 — SQL의 now()는 DB 세션 타임존이라 다른 행과 어긋난다
        LocalDateTime now = LocalDateTime.now();

        entityManager.createNativeQuery(INSERT_NONE_PAIR)
                .setParameter("firstUserId", rows.get(0)[0])
                .setParameter("firstTargetId", rows.get(0)[1])
                .setParameter("secondUserId", rows.get(1)[0])
                .setParameter("secondTargetId", rows.get(1)[1])
                .setParameter("type", RelationshipType.NONE.name())
                .setParameter("now", now)
                .executeUpdate();
    }

    // 조건을 쿼리 문자열로 붙인다 — ":param IS NULL OR ..."로 쓰면 null 파라미터의 타입을 PostgreSQL이 추론하지 못한다
    @Override
    public List<FriendRow> loadGroup(UserId ownerId, UserId viewerId, RelationshipType type,
                                     String nicknameFilter, FriendKeyset after, int limit) {
        String jpql = SELECT_FRIENDS
                + (nicknameFilter == null ? "" : NICKNAME_CONDITION)
                + (after == null ? "" : AFTER_CONDITION)
                + ORDER_FRIENDS;

        TypedQuery<FriendRow> query = entityManager.createQuery(jpql, FriendRow.class)
                .setParameter("ownerId", ownerId.value())
                .setParameter("viewerId", viewerId.value())
                .setParameter("type", type)
                .setMaxResults(limit);
        if (nicknameFilter != null) {
            query.setParameter("nicknamePattern", LikePatterns.containing(nicknameFilter));
        }
        if (after != null) {
            query.setParameter("afterUpdatedAt", after.updatedAt())
                    .setParameter("afterUserId", after.userId());
        }
        return query.getResultList();
    }

    // 조회자 행만 읽는다 — (user_id, target_id) UNIQUE를 user_id 선두로 탄다.
    // 행이 없는 사람은 결과에서 빠지고, 호출자가 NONE으로 본다
    @Override
    public Map<UUID, RelationshipType> loadTypes(UserId viewerId, Collection<UUID> targetIds) {
        return entityManager.createQuery("""
                        SELECT r FROM UserRelationshipJpaEntity r
                        WHERE r.userId = :viewerId
                          AND r.targetId IN :targetIds
                        """, UserRelationshipJpaEntity.class)
                .setParameter("viewerId", viewerId.value())
                .setParameter("targetIds", targetIds)
                .getResultStream()
                .collect(Collectors.toMap(UserRelationshipJpaEntity::getTargetId, UserRelationshipJpaEntity::getType));
    }

    private static UserRelationship toDomain(UserRelationshipJpaEntity entity) {
        return UserRelationship.builder()
                .userRelationshipId(entity.getUserRelationshipId())
                .userId(entity.getUserId())
                .targetId(entity.getTargetId())
                .type(entity.getType())
                .build();
    }
}
