package com.runiverse.running_service.infrastructure.persistence.running;

import com.runiverse.running_service.application.match.port.out.LoadMatchCandidatesPort;
import com.runiverse.running_service.application.match.port.out.LoadMatchPlayersPort;
import com.runiverse.running_service.application.match.port.out.LoadMatchRoomPort;
import com.runiverse.running_service.application.match.port.out.MatchCandidate;
import com.runiverse.running_service.application.match.port.out.MatchPlayer;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.running.room.vo.RunningRoomId;
import com.runiverse.running_service.domain.running.room.vo.RunningRoomStatus;
import com.runiverse.running_service.domain.running.room.vo.RunningRoomType;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

// 매칭 단계의 조회만 담는다 — 방·참가자 애그리거트의 저장은 RunningPersistenceAdapter 몫이다
@Component
@RequiredArgsConstructor
public class MatchPersistenceAdapter implements LoadMatchRoomPort, LoadMatchPlayersPort, LoadMatchCandidatesPort {

    private final EntityManager entityManager;

    @Override
    public Optional<RunningRoomId> findAssignedRoom(UserId userId) {
        return entityManager.createQuery("""
                        SELECT s.room.runningRoomId
                        FROM RunningRoomSessionJpaEntity s
                        WHERE s.userId = :userId
                          AND s.connected = TRUE
                        """, Long.class)
                .setParameter("userId", userId.value())
                // 현재 배정된 행은 하나다(erd) — 어긋나도 깨지지 않게 첫 건만 쓴다
                .getResultStream()
                .findFirst()
                .map(RunningRoomId::new);
    }

    @Override
    public List<MatchPlayer> loadPlayers(RunningRoomId runningRoomId) {
        return entityManager.createQuery("""
                        SELECT NEW com.runiverse.running_service.application.match.port.out.MatchPlayer(
                            p.userId, p.avgPace)
                        FROM RunningRoomSessionJpaEntity s
                        JOIN RunningPlayerJpaEntity p
                            ON p.runningPlayerId = s.runningPlayerId
                        WHERE s.room.runningRoomId = :runningRoomId
                          AND s.connected = TRUE
                        ORDER BY p.runningPlayerId
                        """, MatchPlayer.class)
                .setParameter("runningRoomId", runningRoomId.value())
                .getResultList();
    }

    @Override
    public List<MatchCandidate> loadCandidates(UserId userId, LocalDateTime startAt,
                                               int targetDistanceMeters) {
        return entityManager.createQuery("""
                        SELECT NEW com.runiverse.running_service.application.match.port.out.MatchCandidate(
                            r.runningRoomId, r.avgPace, COALESCE(s.leaveCount, 0))
                        FROM RunningRoomJpaEntity r
                        LEFT JOIN RunningRoomSessionJpaEntity s
                            ON s.room = r AND s.userId = :userId
                        WHERE r.deletedAt IS NULL
                          AND r.type = :type
                          AND r.status = :status
                          AND r.startAt = :startAt
                          AND r.targetDistance = :targetDistance
                          AND r.currentPlayerCount < r.maxPlayerCount
                          AND r.avgPace IS NOT NULL
                        """, MatchCandidate.class)
                // 솔로·초대 방을 인덱스 단계에서 배제한다(erd 후보 방 조회 인덱스)
                .setParameter("type", RunningRoomType.MATCH)
                .setParameter("status", RunningRoomStatus.MATCHING)
                .setParameter("startAt", startAt)
                .setParameter("targetDistance", targetDistanceMeters)
                // 세션 PK가 (running_room_id, user_id)라 이 조인은 방당 최대 한 행이다 —
                // 집계(GROUP BY·SUM)가 필요 없다
                .setParameter("userId", userId.value())
                .getResultList();
    }

}
