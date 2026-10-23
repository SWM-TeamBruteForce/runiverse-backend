package com.runiverse.running_service.infrastructure.persistence.running;

import com.runiverse.running_service.application.running.port.out.CreateRunningRecordPort;
import com.runiverse.running_service.application.running.port.out.ExistsRunningRecordPort;
import com.runiverse.running_service.application.running.port.out.LoadMyRunningRecordsPort;
import com.runiverse.running_service.application.running.port.out.LoadRecentRunningPacesPort;
import com.runiverse.running_service.application.running.port.out.LoadRecordedRoutesPort;
import com.runiverse.running_service.application.running.port.out.RecentRunningPace;
import com.runiverse.running_service.application.running.port.out.RecordedRoute;
import com.runiverse.running_service.application.running.port.out.RecordedSplitTime;
import com.runiverse.running_service.application.running.port.out.RunningRecordRow;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.running.player.vo.RunningPlayerStatus;
import com.runiverse.running_service.domain.running.record.RunningRecord;
import com.runiverse.running_service.domain.running.record.RunningSegment;
import com.runiverse.running_service.domain.running.record.RunningSplit;
import com.runiverse.running_service.domain.running.room.vo.RunningRoomId;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class RunningRecordPersistenceAdapter implements CreateRunningRecordPort, ExistsRunningRecordPort,
        LoadRecentRunningPacesPort, LoadMyRunningRecordsPort, LoadRecordedRoutesPort {

    // 방당 수천 행이라 영속성 컨텍스트를 비워가며 넣는다.
    // hibernate.jdbc.batch_size와 맞춰야 실제로 묶여 나간다
    private static final int BATCH_SIZE = 100;
    private static final List<RunningPlayerStatus> STARTED_STATUSES =
            Arrays.stream(RunningPlayerStatus.values())
                    .filter(RunningPlayerStatus::hasStartedRunning)
                    .toList();
    private final EntityManager entityManager;

    @Override
    public void create(RunningRecord record) {
        if (!record.isNew()) {
            throw new IllegalStateException("이미 저장된 기록이다 — 기록은 write-once다");
        }
        // 구간이 참조할 방 프록시. 실제 SELECT 없이 FK 값만 쓴다
        RunningRoomJpaEntity room = entityManager.getReference(
                RunningRoomJpaEntity.class, record.getRunningRoomId().value());
        RunningRecordJpaEntity recordEntity = RunningRecordJpaEntity.create(
                room,
                record.getUserId().value(),
                record.getAvgPace().secondsPerKm(),
                record.getTotalDistance().meters(),
                record.getTotalDuration().seconds(),
                record.getAvgCadence().map(cadence -> cadence.stepsPerMinute()).orElse(null),
                record.getTotalElevationGain().map(gain -> gain.meters()).orElse(null),
                record.getTotalCalories().kcal(),
                record.getGpsTrackKey().value(),
                record.getWeatherCode().value(),
                record.getTemperature().celsius(),
                record.getPeriod().startAt(),
                record.getPeriod().endAt());
        // IDENTITY 전략이라 여기서 INSERT가 나가고 ID가 채워진다 — 조각이 이 ID를 참조한다
        entityManager.persist(recordEntity);
        // 조각도 IDENTITY라 persist마다 ID가 채워진다 — 구간이 조각 번호로 이 ID를 찾는다
        Map<Integer, Long> segmentIds = new HashMap<>();
        for (RunningSegment segment : record.getSegments()) {
            RunningSegmentJpaEntity segmentEntity = RunningSegmentJpaEntity.create(
                    recordEntity,
                    segment.getSegmentNumber().value(),
                    segment.getRoutePolyline().value());
            entityManager.persist(segmentEntity);
            segmentIds.put(segment.getSegmentNumber().value(), segmentEntity.getRunningSegmentId());
        }
        int persisted = 0;
        for (RunningSplit split : record.getSplits()) {
            // clear 뒤에도 쓸 수 있게 ID로 프록시를 얻는다 — 같은 ID는 컨텍스트에서 같은 프록시가 돌아온다
            RunningSegmentJpaEntity segment = entityManager.getReference(
                    RunningSegmentJpaEntity.class, segmentIds.get(split.getSegmentNumber().value()));
            entityManager.persist(toEntity(segment, split));
            // 쌓아두면 방 하나에 수천 개가 컨텍스트에 남아 메모리와 flush 비용이 같이 커진다
            if (++persisted % BATCH_SIZE == 0) {
                entityManager.flush();
                entityManager.clear();
            }
        }
    }

    @Override
    public boolean existsInRoom(RunningRoomId runningRoomId) {
        // running_room_id 인덱스를 탄다. 같은 트랜잭션에서 방금 만든 기록도
        // 이 쿼리 앞의 자동 flush로 함께 보인다
        return entityManager.createQuery("""
                        SELECT COUNT(record)
                        FROM RunningRecordJpaEntity record
                        WHERE record.room.runningRoomId = :roomId
                        """, Long.class)
                .setParameter("roomId", runningRoomId.value())
                .getSingleResult() > 0;
    }

    @Override
    public List<RecentRunningPace> loadRecent(UserId userId, int limit) {
        // idx_running_record_user(user_id, start_at)를 탄다.
        // 같은 트랜잭션에서 방금 만든 기록도 이 쿼리 앞의 자동 flush로 함께 보인다.
        // start_at이 겹치면 순서가 흔들리므로 id로 한 번 더 가른다
        return entityManager.createQuery("""
                        SELECT NEW com.runiverse.running_service.application.running.port.out.RecentRunningPace(
                            record.totalDistance, record.totalDuration)
                        FROM RunningRecordJpaEntity record
                        WHERE record.userId = :userId
                        ORDER BY record.startAt DESC, record.runningRecordId DESC
                        """, RecentRunningPace.class)
                .setParameter("userId", userId.value())
                .setMaxResults(limit)
                .getResultList();
    }

    @Override
    public List<RunningRecordRow> loadByPeriod(UserId userId, LocalDateTime startInclusive,
                                               LocalDateTime endExclusive) {
        // 숨긴 방의 기록은 목록에서도 뺀다 — 결과 조회가 그 방을 없는 방으로 본다
        return entityManager.createQuery("""
                        SELECT NEW com.runiverse.running_service.application.running.port.out.RunningRecordRow(
                            record.runningRecordId, record.room.runningRoomId, record.room.type,
                            (SELECT COUNT(player)
                             FROM RunningRoomSessionJpaEntity session
                             JOIN RunningPlayerJpaEntity player
                                 ON player.runningPlayerId = session.runningPlayerId
                             WHERE session.room = record.room
                               AND player.status IN :startedStatuses),
                            record.startAt, record.totalDistance, record.totalDuration, record.avgPace,
                            record.totalElevationGain)
                        FROM RunningRecordJpaEntity record
                        WHERE record.userId = :userId
                          AND record.room.deletedAt IS NULL
                          AND record.startAt >= :startInclusive
                          AND record.startAt < :endExclusive
                        ORDER BY record.startAt, record.runningRecordId
                        """, RunningRecordRow.class)
                .setParameter("userId", userId.value())
                .setParameter("startedStatuses", STARTED_STATUSES)
                .setParameter("startInclusive", startInclusive)
                .setParameter("endExclusive", endExclusive)
                .getResultList();
    }

    // 기록마다 조각 polyline을 번호순으로 모은다 — 기록마다 따로 부르면 기록 수만큼 쿼리가 나간다.
    // uk_running_segment_record_number(running_record_id, segment_number)가 조회와 정렬을 맡는다
    private Map<Long, List<String>> loadSegmentPolylines(Collection<Long> recordIds) {
        return entityManager.createQuery("""
                        SELECT segment.record.runningRecordId AS recordId,
                            segment.routePolyline AS routePolyline
                        FROM RunningSegmentJpaEntity segment
                        WHERE segment.record.runningRecordId IN :recordIds
                        ORDER BY segment.record.runningRecordId, segment.segmentNumber
                        """, Tuple.class)
                .setParameter("recordIds", recordIds)
                .getResultList()
                .stream()
                .collect(Collectors.groupingBy(
                        segment -> segment.get("recordId", Long.class),
                        Collectors.mapping(segment -> segment.get("routePolyline", String.class),
                                Collectors.toList())));
    }

    // 콤보 달성 위치를 고를 재료 — 기록·조각·구간 시각을 나눠 읽는다.
    // 한 번에 JOIN하면 구간 행마다 조각 route_polyline(text)이 되풀이돼 방 하나에 수천 번 실려 온다.
    // 엔티티가 아니라 값만 읽는다 — 영속성 컨텍스트에 구간 수천 개를 올릴 이유가 없다
    @Override
    public List<RecordedRoute> loadRoutes(Long runningRoomId) {
        // uk_running_record_room_user(running_room_id, user_id)를 탄다
        List<Tuple> records = entityManager.createQuery("""
                        SELECT record.runningRecordId AS recordId, record.userId AS userId
                        FROM RunningRecordJpaEntity record
                        WHERE record.room.runningRoomId = :roomId
                        """, Tuple.class)
                .setParameter("roomId", runningRoomId)
                .getResultList();
        if (records.isEmpty()) {
            return List.of();
        }
        Map<Long, List<String>> segments = loadSegmentPolylines(
                records.stream().map(record -> record.get("recordId", Long.class)).toList());
        Map<Long, List<RecordedSplitTime>> splitsByRecord = entityManager.createQuery("""
                        SELECT segment.record.runningRecordId AS recordId,
                            segment.segmentNumber AS segmentNumber,
                            split.routeStartIndex AS routeStartIndex, split.routeEndIndex AS routeEndIndex,
                            split.startAt AS startAt, split.endAt AS endAt
                        FROM RunningSplitJpaEntity split
                        JOIN split.segment segment
                        WHERE segment.record.room.runningRoomId = :roomId
                        ORDER BY segment.record.runningRecordId, split.splitNumber
                        """, Tuple.class)
                .setParameter("roomId", runningRoomId)
                .getResultList()
                .stream()
                .collect(Collectors.groupingBy(
                        split -> split.get("recordId", Long.class),
                        Collectors.mapping(split -> new RecordedSplitTime(
                                split.get("segmentNumber", Integer.class),
                                split.get("routeStartIndex", Integer.class),
                                split.get("routeEndIndex", Integer.class),
                                split.get("startAt", LocalDateTime.class),
                                split.get("endAt", LocalDateTime.class)), Collectors.toList())));
        return records.stream()
                .map(record -> new RecordedRoute(
                        record.get("userId", UUID.class),
                        segments.getOrDefault(record.get("recordId", Long.class), List.of()),
                        splitsByRecord.getOrDefault(record.get("recordId", Long.class), List.of())))
                .toList();
    }

    private RunningSplitJpaEntity toEntity(RunningSegmentJpaEntity segment, RunningSplit split) {
        return RunningSplitJpaEntity.create(
                segment,
                split.getSplitNumber().value(),
                split.getAvgPace().secondsPerKm(),
                split.getDistance().meters(),
                split.getDuration().seconds(),
                split.getAvgCadence().map(cadence -> cadence.stepsPerMinute()).orElse(null),
                split.getElevationChange().map(change -> change.meters()).orElse(null),
                split.getCalories().kcal(),
                split.getRouteRange().startIndex(),
                split.getRouteRange().endIndex(),
                split.getPeriod().startAt(),
                split.getPeriod().endAt());
    }
}
