package com.runiverse.running_service.application.running.command.progress;

import com.runiverse.running_service.application.running.port.in.BroadcastRunningProgressUsecase;
import com.runiverse.running_service.application.running.port.out.LoadRunningRoomMembersPort;
import com.runiverse.running_service.application.running.port.out.RunningSessionPort;
import com.runiverse.running_service.domain.common.vo.UserId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class BroadcastRunningProgressHandler implements BroadcastRunningProgressUsecase {

    private final LoadRunningRoomMembersPort loadRunningRoomMembersPort;
    private final RunningSessionPort runningSessionPort;

    // 이 인스턴스에 붙어 있는 참가자에게만 보낸다 — 다른 서버의 참가자는 그쪽이 같은 메시지를 받아 처리한다
    @Override
    public void handle(BroadcastRunningProgressCommand command) {
        UserId sender = new UserId(command.progress().userId());
        loadRunningRoomMembersPort.usersIn(command.runningRoomId()).stream()
                .filter(userId -> !userId.equals(sender))
                .map(runningSessionPort::find)
                .flatMap(Optional::stream)
                .forEach(connection -> connection.sendProgress(command.progress()));
    }
}
