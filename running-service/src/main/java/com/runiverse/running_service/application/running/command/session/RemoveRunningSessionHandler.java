package com.runiverse.running_service.application.running.command.session;

import com.runiverse.running_service.application.running.common.LiveRunningStatusChanger;
import com.runiverse.running_service.application.running.port.in.RemoveRunningSessionUsecase;
import com.runiverse.running_service.application.running.port.out.LiveRunningStatus;
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
    private final LiveRunningStatusChanger liveRunningStatusChanger;

    @Override
    public void handle(RemoveRunningSessionCommand command) {
        UserId userId = new UserId(command.userId());
        // 실제로 내 연결이 빠졌을 때만 방에서도 뺀다 — 새 연결이 가져간 자리는 건드리지 않는다
        if (runningSessionPort.remove(userId, command.connection())) {
            runningRoomMembershipPort.leave(userId);
            // 시작 전에 끊긴 연결은 방이 없다 — 알릴 상대도 없다.
            // 종료 ack를 받고 닫은 연결은 FINISHED가 막아 끊김으로 퍼지지 않는다
            if (command.runningRoomId() != null) {
                liveRunningStatusChanger.change(command.runningRoomId(), userId,
                        command.targetDistanceMeters(), LiveRunningStatus.DISCONNECTED);
            }
        }
    }
}
