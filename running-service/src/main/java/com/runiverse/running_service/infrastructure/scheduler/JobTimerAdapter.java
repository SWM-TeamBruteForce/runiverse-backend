package com.runiverse.running_service.infrastructure.scheduler;

import com.runiverse.running_service.application.scheduling.command.run.RunScheduledJobCommand;
import com.runiverse.running_service.application.scheduling.port.in.RunScheduledJobUsecase;
import com.runiverse.running_service.application.scheduling.port.out.RegisterJobTimerPort;
import com.runiverse.running_service.domain.scheduling.ScheduledJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

// 주기적으로 훑지 않고 예약된 그 시각에만 깬다.
// 대기 중인 예약은 스레드를 잡지 않고 지연 큐에 앉아 있다가 발화 때만 하나 쓴다
@Slf4j
@Component
@RequiredArgsConstructor
public class JobTimerAdapter implements RegisterJobTimerPort {

    private final TaskScheduler taskScheduler;
    private final RunScheduledJobUsecase runScheduledJobUsecase;
    // 같은 예약이 부팅 복구·전파·본인 발행으로 여러 번 들어온다 — 인스턴스 안에서는 하나만 남긴다.
    // 인스턴스 사이의 중복은 여기서 막지 않는다(그게 이중화다). 잠금과 markSent()가 거른다
    private final Map<Long, ScheduledFuture<?>> timers = new ConcurrentHashMap<>();

    @Override
    public void register(ScheduledJob job) {
        Long jobId = job.getScheduledJobId()
                .orElseThrow(() -> new IllegalStateException("저장되지 않은 예약은 걸 수 없다"))
                .value();
        timers.computeIfAbsent(jobId, id -> taskScheduler.schedule(
                () -> fire(id),
                // 저장 시각이 KST 벽시계라 JVM 기본 타임존으로 되돌린다(DefaultTimeZoneInitializer)
                job.getExecuteAt().atZone(ZoneId.systemDefault()).toInstant()));
    }

    private void fire(Long jobId) {
        timers.remove(jobId);
        try {
            runScheduledJobUsecase.handle(new RunScheduledJobCommand(jobId));
        } catch (RuntimeException e) {
            // 스케줄러 스레드로 예외가 올라가면 아무도 못 보고 조용히 사라진다
            log.error("[예약] 예약 실행 실패: 처리하지 못한 예외 - scheduledJobId={}", jobId, e);
        }
    }
}
