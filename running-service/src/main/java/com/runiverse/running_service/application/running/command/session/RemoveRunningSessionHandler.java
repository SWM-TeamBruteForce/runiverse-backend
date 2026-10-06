package com.runiverse.running_service.application.running.command.session;

import com.runiverse.running_service.application.running.port.in.RemoveRunningSessionUsecase;
import com.runiverse.running_service.application.running.port.out.RunningRoomMembershipPort;
import com.runiverse.running_service.application.running.port.out.RunningSessionPort;
import com.runiverse.running_service.domain.common.vo.UserId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RemoveRunningSessionHandler implements RemoveRunningSessionUsecase {

    private final RunningSessionPort runningSessionPort;
    private final RunningRoomMembershipPort runningRoomMembershipPort;

    @Override
    public void handle(RemoveRunningSessionCommand command) {
        UserId userId = new UserId(command.userId());
        // 실제로 내 연결이 빠졌을 때만 방에서도 뺀다 — 새 연결이 가져간 자리는 건드리지 않는다
        if (runningSessionPort.remove(userId, command.connection())) {
            runningRoomMembershipPort.leave(userId);
        }
    }
}
