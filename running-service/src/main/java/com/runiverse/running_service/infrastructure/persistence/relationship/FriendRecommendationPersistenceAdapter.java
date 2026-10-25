package com.runiverse.running_service.infrastructure.persistence.relationship;

import com.runiverse.running_service.application.relationship.port.out.LoadFriendRecommendationsPort;
import com.runiverse.running_service.application.relationship.port.out.RecommendationCandidate;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.relationship.vo.RelationshipType;
import com.runiverse.running_service.domain.running.player.vo.RunningPlayerStatus;
import com.runiverse.running_service.domain.running.room.vo.RunningRoomType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

// 러닝·온보딩 테이블을 함께 읽지만 결과가 관계 추천이라 relationship에 둔다
@Component
@RequiredArgsConstructor
public class FriendRecommendationPersistenceAdapter implements LoadFriendRecommendationsPort {

    private static final List<RunningPlayerStatus> STARTED_STATUSES =
            Arrays.stream(RunningPlayerStatus.values())
                    .filter(RunningPlayerStatus::hasStartedRunning)
                    .toList();
    // 이미 친구이거나 어느 방향이든 요청이 오가는 중이면 친구 요청을 보낼 수 없다 — "맞는 것"을 나열한다(erd 조회 규칙)
    private static final List<RelationshipType> UNAVAILABLE_TYPES = List.of(
            RelationshipType.FRIEND,
            RelationshipType.FRIEND_REQUEST_SENT,
            RelationshipType.FRIEND_REQUEST_RECEIVED);
    // 후보의 닉네임·사진을 붙인다. 온보딩 전인 사람(탈퇴자 포함)은 닉네임이 없어 INNER JOIN에서 빠진다
    private static final String CANDIDATE_SELECT = """
            SELECT NEW com.runiverse.running_service.application.relationship.port.out.RecommendationCandidate(
                o.userId, o.nickname, u.profileImageKey)
            """;
    private static final String NOT_UNAVAILABLE = """
              AND NOT EXISTS (
                  SELECT r FROM UserRelationshipJpaEntity r
                  WHERE r.userId = :userId AND r.targetId = o.userId AND r.type IN :unavailableTypes)
            """;
    private final EntityManager entityManager;

    @Override
    public List<RecommendationCandidate> loadRanTogether(UserId userId, int limit) {
        // 1. 가장 최근 매칭 방 — 솔로·초대 방은 보지 않는다. 숨긴 방은 결과 조회도 없는 방으로 본다
        List<Long> latestRoom = entityManager.createQuery("""
                        SELECT s.room.runningRoomId
                        FROM RunningRoomSessionJpaEntity s
                        JOIN RunningPlayerJpaEntity p ON p.runningPlayerId = s.runningPlayerId
                        WHERE s.userId = :userId
                          AND s.room.type = :matchType
                          AND s.room.deletedAt IS NULL
                          AND p.status IN :startedStatuses
                        ORDER BY s.room.startAt DESC, s.room.runningRoomId DESC
                        """, Long.class)
                .setParameter("userId", userId.value())
                .setParameter("matchType", RunningRoomType.MATCH)
                .setParameter("startedStatuses", STARTED_STATUSES)
                .setMaxResults(1)
                .getResultList();
        if (latestRoom.isEmpty()) {
            return List.of();
        }

        // 2. 그 방에서 함께 러닝 단계에 들어간 사람 — 뛰었는지는 is_connected가 아니라 신청 status가 답한다
        return entityManager.createQuery(CANDIDATE_SELECT + """
                        FROM RunningRoomSessionJpaEntity s
                        JOIN RunningPlayerJpaEntity p ON p.runningPlayerId = s.runningPlayerId
                        JOIN UserOnboardingJpaEntity o ON o.userId = s.userId
                        JOIN UserJpaEntity u ON u.userId = s.userId
                        WHERE s.room.runningRoomId = :roomId
                          AND s.userId <> :userId
                          AND p.status IN :startedStatuses
                        """ + NOT_UNAVAILABLE + """
                        ORDER BY o.userId
                        """, RecommendationCandidate.class)
                .setParameter("roomId", latestRoom.get(0))
                .setParameter("userId", userId.value())
                .setParameter("startedStatuses", STARTED_STATUSES)
                .setParameter("unavailableTypes", UNAVAILABLE_TYPES)
                .setMaxResults(limit)
                .getResultList();
    }

    // idx_user_onboarding_avg_pace로 범위를 읽고 차이순 정렬은 그 범위 안에서만 한다
    @Override
    public List<RecommendationCandidate> loadSimilarPace(UserId userId, int baseSecondsPerKm,
                                                         int toleranceSecondsPerKm,
                                                         Collection<UUID> excludedUserIds, int limit) {
        // 빈 목록의 NOT IN은 SQL 문법이 깨질 수 있어 뺄 사람이 있을 때만 붙인다
        String excluded = excludedUserIds.isEmpty() ? "" : """
                  AND o.userId NOT IN :excludedUserIds
                """;
        TypedQuery<RecommendationCandidate> query = entityManager.createQuery(CANDIDATE_SELECT + """
                        FROM UserOnboardingJpaEntity o
                        JOIN UserJpaEntity u ON u.userId = o.userId
                        WHERE o.avgPace BETWEEN :minPace AND :maxPace
                          AND o.userId <> :userId
                        """ + excluded + NOT_UNAVAILABLE + """
                        ORDER BY ABS(o.avgPace - :basePace), o.userId
                        """, RecommendationCandidate.class)
                .setParameter("minPace", baseSecondsPerKm - toleranceSecondsPerKm)
                .setParameter("maxPace", baseSecondsPerKm + toleranceSecondsPerKm)
                .setParameter("basePace", baseSecondsPerKm)
                .setParameter("userId", userId.value())
                .setParameter("unavailableTypes", UNAVAILABLE_TYPES)
                .setMaxResults(limit);
        if (!excludedUserIds.isEmpty()) {
            query.setParameter("excludedUserIds", excludedUserIds);
        }
        return query.getResultList();
    }
}
