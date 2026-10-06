package com.runiverse.running_service.application.running.command.session;

import com.runiverse.running_service.application.running.port.in.CloseSupersededSessionUsecase;
import com.runiverse.running_service.application.running.port.out.RunningConnection;
import com.runiverse.running_service.application.running.port.out.RunningSessionPort;
import com.runiverse.running_service.domain.common.vo.UserId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CloseSupersededSessionHandler implements CloseSupersededSessionUsecase {

    private final RunningSessionPort runningSessionPort;

    @Override
    public void handle(CloseSupersededSessionCommand command) {
        runningSessionPort.find(new UserId(command.userId()))
                .filter(connection -> !connection.id().equals(command.winnerSessionId()))
                .ifPresent(RunningConnection::closeSuperseded);
    }
}
