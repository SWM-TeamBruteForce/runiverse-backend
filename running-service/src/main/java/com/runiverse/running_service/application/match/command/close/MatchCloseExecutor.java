package com.runiverse.running_service.application.match.command.close;

import com.runiverse.running_service.application.match.port.in.CloseMatchingUsecase;
import com.runiverse.running_service.application.scheduling.ScheduledJobExecutor;
import com.runiverse.running_service.domain.scheduling.vo.JobTarget;
import com.runiverse.running_service.domain.scheduling.vo.ScheduledJobType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

// 예약 기전과 매칭 유스케이스를 잇는 얇은 껍데기 —
// CloseMatchingHandler가 예약을 알지 않게 해서 트리거가 바뀌어도 그대로 남는다
@Component
@RequiredArgsConstructor
public class MatchCloseExecutor implements ScheduledJobExecutor {

    private final CloseMatchingUsecase closeMatchingUsecase;

    @Override
    public ScheduledJobType type() {
        return ScheduledJobType.MATCH_CLOSE;
    }

    @Override
    public void execute(JobTarget target) {
        closeMatchingUsecase.handle(new CloseMatchingCommand(target.idAsLong()));
    }
}
