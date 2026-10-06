package com.runiverse.running_service.application.scheduling.command.recover;

import com.runiverse.running_service.application.scheduling.command.run.RunScheduledJobCommand;
import com.runiverse.running_service.application.scheduling.port.in.RecoverScheduledJobsUsecase;
import com.runiverse.running_service.application.scheduling.port.in.RunScheduledJobUsecase;
import com.runiverse.running_service.application.scheduling.port.out.LoadPendingJobsPort;
import com.runiverse.running_service.application.scheduling.port.out.RegisterJobTimerPort;
import com.runiverse.running_service.domain.scheduling.ScheduledJob;
import com.runiverse.running_service.domain.scheduling.vo.ScheduledJobId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

// 정본은 DB고 타이머는 사본이다 — 부팅 때 사본을 다시 만든다.
// 트랜잭션을 걸지 않는다: 예약 하나가 터져도 나머지는 살아야 하므로
// 실행은 RunScheduledJobHandler가 각자의 트랜잭션에서 한다
@Slf4j
@Service
@RequiredArgsConstructor
public class RecoverScheduledJobsHandler implements RecoverScheduledJobsUsecase {

    private final LoadPendingJobsPort loadPendingJobsPort;
    private final RegisterJobTimerPort registerJobTimerPort;
    private final RunScheduledJobUsecase runScheduledJobUsecase;

    @Override
    public void recover() {
        LocalDateTime now = LocalDateTime.now();
        List<ScheduledJob> pending = loadPendingJobsPort.loadPending();
        log.info("[예약] 복구 시작 - pendingCount={}", pending.size());
        for (ScheduledJob job : pending) {
            try {
                // 내려가 있는 동안 지나간 예약은 기다리지 않고 지금 실행한다
                if (job.isDue(now)) {
                    runScheduledJobUsecase.handle(new RunScheduledJobCommand(
                            job.getScheduledJobId().orElseThrow().value()));
                } else {
                    registerJobTimerPort.register(job);
                }
            } catch (RuntimeException e) {
                // 한 건이 터져 순회가 끊기면 뒤 예약이 통째로 살아나지 못한다
                log.error("[예약] 예약 복구 실패: 처리하지 못한 예외 - scheduledJobId={}",
                        job.getScheduledJobId().map(ScheduledJobId::value).orElse(null), e);
            }
        }
    }
}
