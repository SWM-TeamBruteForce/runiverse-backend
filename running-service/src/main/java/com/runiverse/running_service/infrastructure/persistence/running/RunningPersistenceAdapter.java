package com.runiverse.running_service.infrastructure.persistence.running;

import com.runiverse.running_service.application.match.port.out.CreateMatchApplicationPort;
import com.runiverse.running_service.application.match.port.out.CreateMatchRoomPort;
import com.runiverse.running_service.application.match.port.out.ExistsActiveApplicationPort;
import com.runiverse.running_service.application.match.port.out.LoadActiveApplicationPort;
import com.runiverse.running_service.application.match.port.out.LoadMatchRoomDetailPort;
import com.runiverse.running_service.application.match.port.out.LockMatchApplicationPort;
import com.runiverse.running_service.application.match.port.out.LockMatchRoomPort;
import com.runiverse.running_service.application.match.port.out.UpdateMatchApplicationPort;
import com.runiverse.running_service.application.match.port.out.UpdateMatchRoomPort;
import com.runiverse.running_service.application.running.port.out.CreateRunningPlayerPort;
import com.runiverse.running_service.application.running.port.out.CreateRunningRoomPort;
import com.runiverse.running_service.application.running.port.out.DeleteRunningPlayerPort;
import com.runiverse.running_service.application.running.port.out.ExistsActiveRunningPlayerPort;
import com.runiverse.running_service.application.running.port.out.ExistsRunningPlayerPort;
import com.runiverse.running_service.application.running.port.out.LoadRoomPlayerPort;
import com.runiverse.running_service.application.running.port.out.LoadRunningResultPlayersPort;
import com.runiverse.running_service.application.running.port.out.LoadRunningResultRecordPort;
import com.runiverse.running_service.application.running.port.out.LoadRunningRoomPort;
import com.runiverse.running_service.application.running.port.out.LoadRunningSplitsPort;
import com.runiverse.running_service.application.running.port.out.LoadUserStatusPort;
import com.runiverse.running_service.application.running.port.out.LockRunningPlayerPort;
import com.runiverse.running_service.application.running.port.out.LockRunningRoomPort;
import com.runiverse.running_service.application.running.port.out.RunningResultPlayer;
import com.runiverse.running_service.application.running.port.out.RunningResultRecord;
import com.runiverse.running_service.application.running.port.out.RunningSplitRow;
import com.runiverse.running_service.application.running.port.out.UpdateRunningPlayerPort;
import com.runiverse.running_service.application.running.port.out.UpdateRunningRoomPort;
import com.runiverse.running_service.application.running.port.out.UserStatusRow;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.running.metric.vo.Distance;
import com.runiverse.running_service.domain.running.metric.vo.Pace;
import com.runiverse.running_service.domain.running.player.RunningPlayer;
import com.runiverse.running_service.domain.running.player.vo.RunningPlayerId;
import com.runiverse.running_service.domain.running.player.vo.RunningPlayerStatus;
import com.runiverse.running_service.domain.running.room.RunningRoom;
import com.runiverse.running_service.domain.running.room.SessionDraft;
import com.runiverse.running_service.domain.running.room.vo.RunningRoomId;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class RunningPersistenceAdapter implements CreateRunningPlayerPort, CreateRunningRoomPort,
        ExistsActiveRunningPlayerPort, LoadRunningRoomPort, LockRunningRoomPort, UpdateRunningRoomPort,
        LockRunningPlayerPort, UpdateRunningPlayerPort, DeleteRunningPlayerPort, LoadRoomPlayerPort,
        ExistsRunningPlayerPort, LoadRunningResultPlayersPort, LoadRunningResultRecordPort, LoadRunningSplitsPort,
        // 매칭 유스케이스가 자기 포트로 같은 애그리거트를 다룬다
        CreateMatchApplicationPort, ExistsActiveApplicationPort,
        CreateMatchRoomPort, UpdateMatchRoomPort, LockMatchRoomPort,
        // 취소·나가기가 쓰는 둘 — 시그니처가 같아 기존 메서드가 그대로 만족시킨다
        LoadActiveApplicationPort, LockMatchApplicationPort, UpdateMatchApplicationPort,
        // 스냅샷 조회 — 잠그지 않는 것만 lockById와 다르다
        LoadMatchRoomDetailPort,
        LoadUserStatusPort {

    private final EntityManager entityManager;

    @Override
    public RunningPlayer create(RunningPlayer player) {
        if (!player.isNew()) {
            throw new IllegalStateException("이미 저장된 신청이다 — 종료·상태 변경은 별도 포트로 처리한다");
        }
        RunningPlayerJpaEntity entity = RunningPlayerJpaEntity.create(
                player.getUserId().value(),
                player.getStatus(),
                player.getAvgPace().secondsPerKm(),
                player.getTargetDistance().meters(),
                player.getDesiredPlayerCount().value(),
                player.getStartAt()
        );
        // IDENTITY 전략이라 persist 시점에 INSERT가 나가고 ID가 채워진다 —
        // 방의 세션이 이 ID를 참조하므로 여기서 확보돼야 한다
        entityManager.persist(entity);
        return toDomain(entity);
    }

    @Override
    public RunningRoom create(RunningRoom room) {
        if (!room.isNew()) {
            throw new IllegalStateException("이미 저장된 방이다 — 상태 변경은 별도 포트로 처리한다");
        }
        RunningRoomJpaEntity roomEntity = RunningRoomJpaEntity.create(
                room.getType(),
                room.getStatus(),
                room.getStartAt(),
                room.getTargetDistance().map(Distance::meters).orElse(null),
                room.getAvgPace().map(Pace::secondsPerKm).orElse(null),
                room.getPlayerCount().current(),
                room.getPlayerCount().max()
        );
        entityManager.persist(roomEntity);
        // 세션은 방 애그리거트의 내부 엔티티라 별도 포트 없이 여기서 함께 저장한다.
        // 신청은 다른 애그리거트라 ID 값만 담는다 — 프록시를 잡을 필요가 없어졌다
        List<RunningRoomSessionJpaEntity> sessions = room.getSessions().stream()
                .map(session -> RunningRoomSessionJpaEntity.create(
                        roomEntity,
                        session.getUserId().value(),
                        session.getRunningPlayerId().value(),
                        session.getLeaveCount().value(),
                        session.isConnected()))
                .toList();
        sessions.forEach(entityManager::persist);
        return toDomain(roomEntity, sessions);
    }

    @Override
    public boolean existsActive(UserId userId) {
        Long count = entityManager.createQuery("""
                        SELECT COUNT(p)
                        FROM RunningPlayerJpaEntity p
                        WHERE p.userId = :userId
                          AND p.deletedAt IS NULL
                        """, Long.class)
                .setParameter("userId", userId.value())
                .getSingleResult();
        return count > 0;
    }

    @Override
    public Optional<RunningRoom> loadById(RunningRoomId runningRoomId) {
        return entityManager.createQuery("""
                        SELECT r
                        FROM RunningRoomJpaEntity r
                        WHERE r.runningRoomId = :runningRoomId
                          AND r.deletedAt IS NULL
                        """, RunningRoomJpaEntity.class)
                .setParameter("runningRoomId", runningRoomId.value())
                .getResultStream()
                .findFirst()
                // 세션 없이는 "이 방 참가자인가"를 판정할 수 없다 — 방과 항상 함께 복원한다
                .map(entity -> toDomain(entity, loadSessions(entity)));
    }

    @Override
    public Optional<RunningPlayer> loadActive(UserId userId) {
        return entityManager.createQuery("""
                        SELECT p
                        FROM RunningPlayerJpaEntity p
                        WHERE p.userId = :userId
                          AND p.deletedAt IS NULL
                        """, RunningPlayerJpaEntity.class)
                .setParameter("userId", userId.value())
                // 앱이 한 개만 보장하고 DB는 강제하지 않는다 — 여럿이어도 깨지지 않게 첫 건만 쓴다
                .getResultStream()
                .findFirst()
                .map(this::toDomain);
    }

    @Override
    public void update(RunningRoom room) {
        Long roomId = room.getRunningRoomId()
                .orElseThrow(() -> new IllegalStateException("저장되지 않은 방은 갱신할 수 없다"))
                .value();
        // 영속 상태로 올려두면 더티 체킹이 UPDATE를 만든다
        RunningRoomJpaEntity entity = entityManager.find(RunningRoomJpaEntity.class, roomId);
        entity.changeStatus(room.getStatus());
        entity.changeCloseAt(room.getCloseAt().orElse(null));
        entity.changeAvgPace(room.getAvgPace().map(Pace::secondsPerKm).orElse(null));
        entity.changeCurrentPlayerCount(room.getPlayerCount().current());
        // 세션은 방 애그리거트의 내부 엔티티라 별도 포트 없이 여기서 함께 반영한다.
        // 키는 유저다 — 재배정이면 행을 새로 만들지 않고 신청만 갈아 끼운다
        Map<UUID, RunningRoomSessionJpaEntity> stored = loadSessions(entity).stream()
                .collect(Collectors.toMap(RunningRoomSessionJpaEntity::getUserId,
                        session -> session));
        room.getSessions().forEach(session -> {
            UUID userId = session.getUserId().value();
            Long playerId = session.getRunningPlayerId().value();
            RunningRoomSessionJpaEntity target = stored.get(userId);
            if (target == null) {
                // 처음 이 방에 들어온 유저 — 방은 이미 저장돼 있으니 여기서 만든다
                entityManager.persist(RunningRoomSessionJpaEntity.create(
                        entity, userId, playerId,
                        session.getLeaveCount().value(), session.isConnected()));
                return;
            }
            // 전에 거쳐 간 방에 새 신청으로 다시 들어왔으면 신청이 갈린다
            target.changeRunningPlayerId(playerId);
            target.changeLeaveCount(session.getLeaveCount().value());
            target.changeConnected(session.isConnected());
        });
    }

    @Override
    public void update(RunningPlayer player) {
        Long playerId = player.getRunningPlayerId()
                .orElseThrow(() -> new IllegalStateException("저장되지 않은 신청은 갱신할 수 없다"))
                .value();
        RunningPlayerJpaEntity entity = entityManager.find(RunningPlayerJpaEntity.class, playerId);
        entity.changeStatus(player.getStatus());
        entity.changeDeletedAt(player.getDeletedAt().orElse(null));
    }

    // 세션은 user_id가 아니라 이 신청으로 지운다 — 유저로 지우면 같은 사람이
    // 거쳐 간 다른 방의 세션까지 날아간다
    @Override
    public void delete(RunningPlayer player) {
        Long playerId = player.getRunningPlayerId()
                .orElseThrow(() -> new IllegalStateException("저장되지 않은 신청은 지울 수 없다"))
                .value();
        entityManager.createQuery("""
                        DELETE FROM RunningRoomSessionJpaEntity session
                        WHERE session.runningPlayerId = :playerId
                        """)
                .setParameter("playerId", playerId)
                .executeUpdate();
        entityManager.createQuery("""
                        DELETE FROM RunningPlayerJpaEntity player
                        WHERE player.runningPlayerId = :playerId
                        """)
                .setParameter("playerId", playerId)
                .executeUpdate();
    }

    // deleted_at을 보지 않는다 — 이미 종료된 참가자도 찾아야 RUNNING_FINISH가 멱등이 된다.
    // 방 배정은 세션이 들고 있으므로 세션을 거쳐 참가자를 찾는다
    @Override
    public Optional<RunningPlayer> load(RunningRoomId runningRoomId, UserId userId) {
        return entityManager.createQuery("""
                        SELECT player
                        FROM RunningRoomSessionJpaEntity session
                        JOIN RunningPlayerJpaEntity player
                            ON player.runningPlayerId = session.runningPlayerId
                        WHERE session.room.runningRoomId = :roomId
                          AND player.userId = :userId
                        """, RunningPlayerJpaEntity.class)
                .setParameter("roomId", runningRoomId.value())
                .setParameter("userId", userId.value())
                .getResultStream()
                .findFirst()
                .map(this::toDomain);
    }

    // 아직 RUNNING으로 남은 참가자가 있는지 — 전원 종료돼야 방을 FINISHED로 닫는다.
    // 참가자 전체를 불러와 세지 않고 존재 여부만 묻는다
    @Override
    public boolean existsRunning(RunningRoomId runningRoomId) {
        return entityManager.createQuery("""
                        SELECT COUNT(player)
                        FROM RunningRoomSessionJpaEntity session
                        JOIN RunningPlayerJpaEntity player
                            ON player.runningPlayerId = session.runningPlayerId
                        WHERE session.room.runningRoomId = :roomId
                          AND player.status = :status
                        """, Long.class)
                .setParameter("roomId", runningRoomId.value())
                .setParameter("status", RunningPlayerStatus.RUNNING)
                .getSingleResult() > 0;
    }

    @Override
    public List<RunningResultPlayer> loadPlayers(RunningRoomId runningRoomId) {
        // 세션이 방과 참가자를 잇는다. 기록은 아직 없을 수 있어 LEFT JOIN이다.
        // player.deletedAt은 걸지 않는다 — 완주·이탈에도 찍혀서 끝난 참가자가 통째로 빠진다
        List<Object[]> rows = entityManager.createQuery("""
                        SELECT player, record
                        FROM RunningRoomSessionJpaEntity session
                        JOIN RunningPlayerJpaEntity player
                            ON player.runningPlayerId = session.runningPlayerId
                        LEFT JOIN RunningRecordJpaEntity record
                            ON record.room.runningRoomId = session.room.runningRoomId
                           AND record.userId = player.userId
                        WHERE session.room.runningRoomId = :roomId
                        ORDER BY player.runningPlayerId
                        """, Object[].class)
                .setParameter("roomId", runningRoomId.value())
                .getResultList();
        return rows.stream()
                .map(row -> toResultPlayer(
                        (RunningPlayerJpaEntity) row[0], (RunningRecordJpaEntity) row[1]))
                .toList();
    }

    // loadActive와 조건이 같고 잠그는 것만 다르다 — 같은 행을 고치는 취소·시작이 이걸 쓴다
    @Override
    public Optional<RunningPlayer> lockActive(UserId userId) {
        return entityManager.createQuery("""
                        SELECT p
                        FROM RunningPlayerJpaEntity p
                        WHERE p.userId = :userId
                          AND p.deletedAt IS NULL
                        """, RunningPlayerJpaEntity.class)
                .setParameter("userId", userId.value())
                // 상대가 커밋할 때까지 기다렸다 읽는다 — 기다리지 않으면 취소된 신청을
                // 활성으로 오인해 deleted_at을 null로 되돌린다
                .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                .getResultStream()
                .findFirst()
                .map(this::toDomain);
    }

    @Override
    public Optional<RunningResultRecord> loadRecord(RunningRoomId runningRoomId, UserId userId) {
        // 엔티티를 통째로 읽지 않는다 — 응답에 필요한 컬럼만 가져온다
        return entityManager.createQuery("""
                        SELECT NEW com.runiverse.running_service.application.running.port.out.RunningResultRecord(
                            record.routePolyline, record.startAt, record.endAt, record.totalDistance, record.totalElevationGain)
                        FROM RunningRecordJpaEntity record
                        WHERE record.room.runningRoomId = :roomId
                          AND record.userId = :userId
                        """, RunningResultRecord.class)
                .setParameter("roomId", runningRoomId.value())
                .setParameter("userId", userId.value())
                .getResultStream()
                .findFirst();
    }

    @Override
    public List<RunningSplitRow> loadSplits(RunningRoomId runningRoomId) {
        // 방의 모든 기록에 딸린 구간을 한 번에 긁는다 — 참가자·구간별로 나눠 부르면 수백 번 나간다.
        // 참가자 구분은 record.userId로 하고, 묶는 것은 핸들러가 splitNumber로 한다
        return entityManager.createQuery("""
                        SELECT NEW com.runiverse.running_service.application.running.port.out.RunningSplitRow(
                            record.userId, split.splitNumber, split.distance, split.duration,
                            split.avgPace, split.avgCadence, split.elevationChange, split.calories,
                            split.routeStartIndex, split.routeEndIndex)
                        FROM RunningSplitJpaEntity split
                        JOIN split.record record
                        WHERE record.room.runningRoomId = :roomId
                        ORDER BY split.splitNumber, record.userId
                        """, RunningSplitRow.class)
                .setParameter("roomId", runningRoomId.value())
                .getResultList();
    }

    @Override
    public Optional<RunningRoom> lockById(RunningRoomId runningRoomId) {
        return entityManager.createQuery("""
                        SELECT r
                        FROM RunningRoomJpaEntity r
                        WHERE r.runningRoomId = :runningRoomId
                          AND r.deletedAt IS NULL
                        """, RunningRoomJpaEntity.class)
                .setParameter("runningRoomId", runningRoomId.value())
                // 방 행만 잠근다 — 인원 갱신이 겹치면 정원을 넘길 수 있다.
                // 세션은 별도 조회라 잠기지 않는다(같은 방의 다른 신청과만 경쟁한다)
                .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                .getResultStream()
                .findFirst()
                .map(entity -> toDomain(entity, loadSessions(entity)));
    }

    // 스냅샷 조회가 신청·취소와 경합하면 안 된다 — 여긴 잠그지 않는 것만 lockById와 다르다.
    // 세션까지 함께 복원한다(방 애그리거트는 세션 없이는 판정할 수 없다)
    @Override
    public Optional<RunningRoom> loadDetailById(RunningRoomId runningRoomId) {
        return entityManager.createQuery("""
                        SELECT r
                        FROM RunningRoomJpaEntity r
                        WHERE r.runningRoomId = :runningRoomId
                          AND r.deletedAt IS NULL
                        """, RunningRoomJpaEntity.class)
                .setParameter("runningRoomId", runningRoomId.value())
                .getResultStream()
                .findFirst()
                .map(entity -> toDomain(entity, loadSessions(entity)));
    }

    // 활성 신청과 현재 배정된 방을 한 번에 읽는다 — 상태 판정에 필요한 값만 뽑아
    // 애그리거트를 올리지 않는다. 방 미배정 상태는 없으므로 조인이 비면 신청도 없는 것이다
    @Override
    public Optional<UserStatusRow> loadStatus(UserId userId) {
        return entityManager.createQuery("""
                        SELECT NEW com.runiverse.running_service.application.running.port.out.UserStatusRow(
                            s.room.runningRoomId, s.room.type, s.room.status,
                            s.room.startAt, s.room.targetDistance)
                        FROM RunningPlayerJpaEntity p
                        JOIN RunningRoomSessionJpaEntity s
                          ON s.runningPlayerId = p.runningPlayerId
                        WHERE p.userId = :userId
                          AND p.deletedAt IS NULL
                          AND s.connected = TRUE
                        """, UserStatusRow.class)
                .setParameter("userId", userId.value())
                // 활성 신청도 배정 행도 하나씩이다 — 어긋나도 깨지지 않게 첫 건만 쓴다
                .getResultStream()
                .findFirst();
    }

    private RunningResultPlayer toResultPlayer(RunningPlayerJpaEntity player,
                                               RunningRecordJpaEntity record) {
        // 아직 안 끝난 참가자 — 사용자 정보와 status만 채워 "기록 없음"으로 보낸다
        if (record == null) {
            return new RunningResultPlayer(player.getUserId(), player.getStatus(),
                    null, null, null, null, null, null);
        }
        return new RunningResultPlayer(
                player.getUserId(),
                player.getStatus(),
                record.getTotalDistance(),
                record.getTotalDuration(),
                record.getTotalCalories(),
                record.getAvgPace(),
                record.getAvgCadence(),
                record.getTotalElevationGain());
    }

    private List<RunningRoomSessionJpaEntity> loadSessions(RunningRoomJpaEntity room) {
        return entityManager.createQuery("""
                        SELECT s
                        FROM RunningRoomSessionJpaEntity s
                        WHERE s.room = :room
                        """, RunningRoomSessionJpaEntity.class)
                .setParameter("room", room)
                .getResultList();
    }

    private RunningPlayer toDomain(RunningPlayerJpaEntity entity) {
        return RunningPlayer.builder()
                .runningPlayerId(entity.getRunningPlayerId())
                .userId(entity.getUserId())
                .status(entity.getStatus())
                .avgPace(entity.getAvgPace())
                .targetDistance(entity.getTargetDistance())
                .desiredPlayerCount(entity.getDesiredPlayerCount())
                .startAt(entity.getStartAt())
                .deletedAt(entity.getDeletedAt())
                .build();
    }

    private RunningRoom toDomain(RunningRoomJpaEntity entity,
                                 List<RunningRoomSessionJpaEntity> sessions) {
        return RunningRoom.builder()
                .runningRoomId(entity.getRunningRoomId())
                .type(entity.getType())
                .status(entity.getStatus())
                .startAt(entity.getStartAt())
                .closeAt(entity.getCloseAt())
                .targetDistance(entity.getTargetDistance())
                .avgPace(entity.getAvgPace())
                .currentPlayerCount(entity.getCurrentPlayerCount())
                .maxPlayerCount(entity.getMaxPlayerCount())
                .sessions(sessions.stream()
                        .map(s -> new SessionDraft(
                                new UserId(s.getUserId()),
                                new RunningPlayerId(s.getRunningPlayerId()),
                                s.getLeaveCount(), s.isConnected()))
                        .toList())
                .build();
    }
}
