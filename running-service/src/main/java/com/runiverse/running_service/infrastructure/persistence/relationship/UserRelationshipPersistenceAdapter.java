package com.runiverse.running_service.infrastructure.persistence.relationship;

import com.runiverse.running_service.application.relationship.port.out.EnsureUserRelationshipPairPort;
import com.runiverse.running_service.application.relationship.port.out.LoadUserRelationshipPort;
import com.runiverse.running_service.application.relationship.port.out.SaveUserRelationshipPort;
import com.runiverse.running_service.application.relationship.port.out.UserRelationshipPair;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.relationship.UserRelationship;
import com.runiverse.running_service.domain.relationship.vo.RelationshipType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

@Component
@RequiredArgsConstructor
public class UserRelationshipPersistenceAdapter implements LoadUserRelationshipPort, SaveUserRelationshipPort,
        EnsureUserRelationshipPairPort {

    // JPQL에는 ON CONFLICT가 없어 네이티브로 쓴다. 이미 있는 행은 건드리지 않고, 다른 트랜잭션이 막 넣은 행이면
    // 그 커밋을 기다렸다 지나간다 — UNIQUE 위반이 나지 않는다
    private static final String INSERT_NONE_PAIR = """
            INSERT INTO user_relationships (user_id, target_id, type, created_at, updated_at)
            VALUES (:firstUserId, :firstTargetId, :type, :now, :now),
                   (:secondUserId, :secondTargetId, :type, :now, :now)
            ON CONFLICT (user_id, target_id) DO NOTHING
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

    private static UserRelationship toDomain(UserRelationshipJpaEntity entity) {
        return UserRelationship.builder()
                .userRelationshipId(entity.getUserRelationshipId())
                .userId(entity.getUserId())
                .targetId(entity.getTargetId())
                .type(entity.getType())
                .build();
    }
}
