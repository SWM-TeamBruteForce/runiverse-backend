package com.runiverse.running_service.application.running.command.session;

import com.runiverse.running_service.application.running.port.in.RegisterRunningSessionUsecase;
import com.runiverse.running_service.application.running.port.out.PublishSupersedePort;
import com.runiverse.running_service.application.running.port.out.RunningConnection;
import com.runiverse.running_service.application.running.port.out.RunningRoomMembershipPort;
import com.runiverse.running_service.application.running.port.out.RunningSessionPort;
import com.runiverse.running_service.domain.common.vo.UserId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RegisterRunningSessionHandler implements RegisterRunningSessionUsecase {

    private final RunningSessionPort runningSessionPort;
    private final RunningRoomMembershipPort runningRoomMembershipPort;
    private final PublishSupersedePort publishSupersedePort;

    @Override
    public void handle(RegisterRunningSessionCommand command) {
        RunningConnection connection = command.connection();
        UserId userId = new UserId(command.userId());
        // 이 인스턴스에 남아 있던 옛 연결은 통지를 기다리지 않고 바로 닫는다.
        // Redis가 죽어 있어도 같은 인스턴스 안에서는 규칙이 지켜져야 한다
        runningSessionPort.register(userId, connection)
                .ifPresent(RunningConnection::closeSuperseded);
        // 발행보다 먼저 구독해야 자기 통지를 되받아 승자 판정이 돈다
        runningRoomMembershipPort.join(userId, command.runningRoomId());
        // 다른 인스턴스가 들고 있을지 모를 옛 연결까지 닫게 알린다
        publishSupersedePort.publish(command.userId(), command.runningRoomId(), connection.id());
    }
}
