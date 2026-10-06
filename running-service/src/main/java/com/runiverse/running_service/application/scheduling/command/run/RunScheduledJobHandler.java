package com.runiverse.running_service.application.scheduling.command.run;

import com.runiverse.running_service.application.scheduling.ScheduledJobExecutor;
import com.runiverse.running_service.application.scheduling.port.in.RunScheduledJobUsecase;
import com.runiverse.running_service.application.scheduling.port.out.LockScheduledJobPort;
import com.runiverse.running_service.application.scheduling.port.out.UpdateScheduledJobPort;
import com.runiverse.running_service.domain.scheduling.ScheduledJob;
import com.runiverse.running_service.domain.scheduling.vo.JobTarget;
import com.runiverse.running_service.domain.scheduling.vo.ScheduledJobId;
import com.runiverse.running_service.domain.scheduling.vo.ScheduledJobType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

// 예약을 선점하고 타입별 실행기로 넘긴다.
// 선점과 실행이 한 트랜잭션이다 — 실행이 터지면 is_sent도 함께 롤백돼
// 잠금을 기다리던 인스턴스가 정상적으로 다시 시도한다
@Slf4j
@Service
@Transactional
public class RunScheduledJobHandler implements RunScheduledJobUsecase {

    private final LockScheduledJobPort lockScheduledJobPort;
    private final UpdateScheduledJobPort updateScheduledJobPort;
    private final Map<ScheduledJobType, ScheduledJobExecutor> executors;

    public RunScheduledJobHandler(LockScheduledJobPort lockScheduledJobPort,
                                  UpdateScheduledJobPort updateScheduledJobPort,
                                  List<ScheduledJobExecutor> executors) {
        this.lockScheduledJobPort = lockScheduledJobPort;
        this.updateScheduledJobPort = updateScheduledJobPort;
        this.executors = executors.stream()
                .collect(Collectors.toMap(ScheduledJobExecutor::type, Function.identity()));
    }

    @Override
    public void handle(RunScheduledJobCommand command) {
        // 1. 여러 인스턴스가 같은 예약을 타이머로 들고 있다 — 잠그고 다시 읽어야 하나만 통과한다.
        // 잠그지 않으면 둘 다 sent=false를 보고 함께 지나간다
        Optional<ScheduledJob> locked =
                lockScheduledJobPort.lockById(new ScheduledJobId(command.scheduledJobId()));
        if (locked.isEmpty()) {
            log.warn("[예약] 예약 실행 건너뜀: 예약 없음 - scheduledJobId={}", command.scheduledJobId());
            return;
        }
        ScheduledJob job = locked.get();
        // 2. 남이 먼저 이겼다. markSent()가 던지기 전에 조용히 빠진다
        if (job.isSent()) {
            return;
        }
        job.markSent(LocalDateTime.now());
        updateScheduledJobPort.update(job);
        JobTarget target = job.getTarget();
        executorOf(target.type()).execute(target);
    }

    private ScheduledJobExecutor executorOf(ScheduledJobType type) {
        ScheduledJobExecutor executor = executors.get(type);
        if (executor == null) {
            // 타입을 추가하고 실행기를 빠뜨린 경우다 — 컴파일러가 못 잡는 자리라 여기서 세운다
            throw new IllegalStateException("예약 타입의 실행기가 없다 — type=" + type);
        }
        return executor;
    }
}
