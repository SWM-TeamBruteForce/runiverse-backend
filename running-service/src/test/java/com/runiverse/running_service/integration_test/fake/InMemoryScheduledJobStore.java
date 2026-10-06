package com.runiverse.running_service.integration_test.fake;

import com.runiverse.running_service.application.scheduling.port.out.LoadPendingJobsPort;
import com.runiverse.running_service.application.scheduling.port.out.LockScheduledJobPort;
import com.runiverse.running_service.application.scheduling.port.out.SaveScheduledJobPort;
import com.runiverse.running_service.application.scheduling.port.out.UpdateScheduledJobPort;
import com.runiverse.running_service.domain.scheduling.ScheduledJob;
import com.runiverse.running_service.domain.scheduling.vo.JobTarget;
import com.runiverse.running_service.domain.scheduling.vo.ScheduledJobId;
import com.runiverse.running_service.domain.scheduling.vo.ScheduledJobType;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

// ScheduledJobPersistenceAdapter를 대신한다 — bigserial ID를 채우고,
// 읽을 때마다 분리된 객체를 준다(저장본을 그대로 주면 update()를 빼먹어도 통과한다)
public class InMemoryScheduledJobStore implements SaveScheduledJobPort, LoadPendingJobsPort,
        LockScheduledJobPort, UpdateScheduledJobPort {

    private final Map<Long, ScheduledJob> jobs = new LinkedHashMap<>();
    private long nextJobId = 1L;

    @Override
    public ScheduledJob save(ScheduledJob job) {
        if (!job.isNew()) {
            throw new IllegalStateException("이미 저장된 예약이다 — 발화 처리는 별도 포트로 한다");
        }
        // 실제 어댑터처럼 UNIQUE(job_type, target_id)를 먼저 본다 — 이미 있으면 그 예약을 쓴다
        Optional<ScheduledJob> existing = findBy(job.getTarget());
        if (existing.isPresent()) {
            return existing.get();
        }
        long id = nextJobId++;
        ScheduledJob saved = ScheduledJob.builder()
                .scheduledJobId(id)
                .target(job.getTarget())
                .executeAt(job.getExecuteAt())
                .build();
        jobs.put(id, saved);
        return copy(saved);
    }

    @Override
    public List<ScheduledJob> loadPending() {
        return jobs.values().stream()
                .filter(job -> !job.isSent())
                .sorted(Comparator.comparing(ScheduledJob::getExecuteAt))
                .map(InMemoryScheduledJobStore::copy)
                .toList();
    }

    @Override
    public Optional<ScheduledJob> lockById(ScheduledJobId scheduledJobId) {
        return Optional.ofNullable(jobs.get(scheduledJobId.value()))
                .map(InMemoryScheduledJobStore::copy);
    }

    @Override
    public void update(ScheduledJob job) {
        long id = job.getScheduledJobId()
                .orElseThrow(() -> new IllegalStateException("저장되지 않은 예약은 갱신할 수 없다"))
                .value();
        if (!jobs.containsKey(id)) {
            throw new IllegalStateException("없는 예약은 갱신할 수 없다");
        }
        jobs.put(id, copy(job));
    }

    // 검증 전용
    public Optional<ScheduledJob> findBy(ScheduledJobType type, Long targetId) {
        return findBy(JobTarget.of(type, targetId)).map(InMemoryScheduledJobStore::copy);
    }

    public int jobCount() {
        return jobs.size();
    }

    private Optional<ScheduledJob> findBy(JobTarget target) {
        return jobs.values().stream()
                .filter(job -> job.getTarget().equals(target))
                .findFirst();
    }

    private static ScheduledJob copy(ScheduledJob job) {
        return ScheduledJob.builder()
                .scheduledJobId(job.getScheduledJobId().map(ScheduledJobId::value).orElse(null))
                .target(job.getTarget())
                .executeAt(job.getExecuteAt())
                .sent(job.isSent())
                .sentAt(job.getSentAt().orElse(null))
                .build();
    }
}
