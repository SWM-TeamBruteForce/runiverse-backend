package com.runiverse.running_service.infrastructure.persistence.running;

import com.runiverse.running_service.application.running.port.out.CreateRunningRecordPort;
import com.runiverse.running_service.application.running.port.out.ExistsRunningRecordPort;
import com.runiverse.running_service.application.running.port.out.LoadRecentRunningPacesPort;
import com.runiverse.running_service.application.running.port.out.RecentRunningPace;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.running.record.RunningRecord;
import com.runiverse.running_service.domain.running.record.RunningSplit;
import com.runiverse.running_service.domain.running.room.vo.RunningRoomId;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class RunningRecordPersistenceAdapter implements CreateRunningRecordPort, ExistsRunningRecordPort,
        LoadRecentRunningPacesPort {

    // 방당 수천 행이라 영속성 컨텍스트를 비워가며 넣는다.
    // hibernate.jdbc.batch_size와 맞춰야 실제로 묶여 나간다
    private static final int BATCH_SIZE = 100;
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
                record.getRoutePolyline().value(),
                record.getWeatherCode().value(),
                record.getTemperature().celsius(),
                record.getPeriod().startAt(),
                record.getPeriod().endAt());
        // IDENTITY 전략이라 여기서 INSERT가 나가고 ID가 채워진다 — 구간이 이 ID를 참조한다
        entityManager.persist(recordEntity);
        int persisted = 0;
        for (RunningSplit split : record.getSplits()) {
            entityManager.persist(toEntity(recordEntity, split));
            // 쌓아두면 방 하나에 수천 개가 컨텍스트에 남아 메모리와 flush 비용이 같이 커진다
            if (++persisted % BATCH_SIZE == 0) {
                entityManager.flush();
                entityManager.clear();
                // clear가 recordEntity까지 준영속으로 만든다 — 다음 구간이 참조할 프록시를 다시 얻는다
                recordEntity = entityManager.getReference(
                        RunningRecordJpaEntity.class, recordEntity.getRunningRecordId());
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

    private RunningSplitJpaEntity toEntity(RunningRecordJpaEntity record, RunningSplit split) {
        return RunningSplitJpaEntity.create(
                record,
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
