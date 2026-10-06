package com.runiverse.running_service.infrastructure.persistence.scheduling;

import com.runiverse.running_service.application.scheduling.port.out.LoadPendingJobsPort;
import com.runiverse.running_service.application.scheduling.port.out.LockScheduledJobPort;
import com.runiverse.running_service.application.scheduling.port.out.SaveScheduledJobPort;
import com.runiverse.running_service.application.scheduling.port.out.UpdateScheduledJobPort;
import com.runiverse.running_service.domain.scheduling.ScheduledJob;
import com.runiverse.running_service.domain.scheduling.vo.JobTarget;
import com.runiverse.running_service.domain.scheduling.vo.ScheduledJobId;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class ScheduledJobPersistenceAdapter implements SaveScheduledJobPort,
        LoadPendingJobsPort, LockScheduledJobPort, UpdateScheduledJobPort {

    private final EntityManager entityManager;

    @Override
    public ScheduledJob save(ScheduledJob job) {
        if (!job.isNew()) {
            throw new IllegalStateException("이미 저장된 예약이다 — 발화 처리는 별도 포트로 한다");
        }
        JobTarget target = job.getTarget();
        // 바로 INSERT를 치면 UNIQUE 위반이 대상 생성 트랜잭션까지 끌고 죽는다.
        // 이미 있으면 그 예약을 그대로 쓴다
        return findBy(target).orElseGet(() -> {
            ScheduledJobJpaEntity entity = ScheduledJobJpaEntity.create(
                    target.type(), target.id(), job.getExecuteAt());
            entityManager.persist(entity);
            // 타이머를 걸려면 ID가 필요하다 — 커밋까지 기다리지 않고 여기서 채운다
            entityManager.flush();
            return toDomain(entity);
        });
    }

    @Override
    public List<ScheduledJob> loadPending() {
        return entityManager.createQuery("""
                        SELECT job
                        FROM ScheduledJobJpaEntity job
                        WHERE job.sent = FALSE
                        ORDER BY job.executeAt ASC
                        """, ScheduledJobJpaEntity.class)
                .getResultList().stream()
                .map(ScheduledJobPersistenceAdapter::toDomain)
                .toList();
    }

    @Override
    public Optional<ScheduledJob> lockById(ScheduledJobId scheduledJobId) {
        return Optional.ofNullable(entityManager.find(
                        ScheduledJobJpaEntity.class, scheduledJobId.value(),
                        LockModeType.PESSIMISTIC_WRITE))
                .map(ScheduledJobPersistenceAdapter::toDomain);
    }

    @Override
    public void update(ScheduledJob job) {
        Long jobId = job.getScheduledJobId()
                .orElseThrow(() -> new IllegalStateException("저장되지 않은 예약은 갱신할 수 없다"))
                .value();
        // 영속 상태로 올려두면 더티 체킹이 UPDATE를 만든다
        ScheduledJobJpaEntity entity = entityManager.find(ScheduledJobJpaEntity.class, jobId);
        entity.changeSent(job.isSent(), job.getSentAt().orElse(null));
    }

    private Optional<ScheduledJob> findBy(JobTarget target) {
        return entityManager.createQuery("""
                        SELECT job
                        FROM ScheduledJobJpaEntity job
                        WHERE job.jobType = :type AND job.targetId = :targetId
                        """, ScheduledJobJpaEntity.class)
                .setParameter("type", target.type())
                .setParameter("targetId", target.id())
                .getResultStream()
                .findFirst()
                .map(ScheduledJobPersistenceAdapter::toDomain);
    }

    private static ScheduledJob toDomain(ScheduledJobJpaEntity entity) {
        return ScheduledJob.builder()
                .scheduledJobId(entity.getScheduledJobId())
                .target(new JobTarget(entity.getJobType(), entity.getTargetId()))
                .executeAt(entity.getExecuteAt())
                .sent(entity.isSent())
                .sentAt(entity.getSentAt())
                .build();
    }
}
