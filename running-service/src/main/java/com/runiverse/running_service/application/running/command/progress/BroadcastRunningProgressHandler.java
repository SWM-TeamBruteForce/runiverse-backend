package com.runiverse.running_service.application.running.command.progress;

import com.runiverse.running_service.application.running.port.in.BroadcastRunningProgressUsecase;
import com.runiverse.running_service.application.running.port.out.LoadRunningRoomMembersPort;
import com.runiverse.running_service.application.running.port.out.RunningSessionPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class BroadcastRunningProgressHandler implements BroadcastRunningProgressUsecase {

    private final LoadRunningRoomMembersPort loadRunningRoomMembersPort;
    private final RunningSessionPort runningSessionPort;

    // 이 인스턴스에 붙어 있는 참가자에게만 보낸다 — 다른 서버의 참가자는 그쪽이 같은 메시지를 받아 처리한다.
    // 보낸 사람도 받는다: 방 전체가 같은 서버 기준값으로 같은 화면을 그리게 하려는 것이다.
    // 다만 본인 표시 거리는 클라의 로컬 계산값이 정본이라, 이 값으로 덮으면 숫자가 되돌아간다
    @Override
    public void handle(BroadcastRunningProgressCommand command) {
        loadRunningRoomMembersPort.usersIn(command.runningRoomId()).stream()
                .map(runningSessionPort::find)
                .flatMap(Optional::stream)
                .forEach(connection -> connection.sendProgress(command.progress()));
    }
}
