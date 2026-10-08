package com.runiverse.running_service.application.running.command.status;

import com.runiverse.running_service.application.running.common.LiveRunningStatusChanger;
import com.runiverse.running_service.application.running.port.in.ChangeLiveRunningStatusUsecase;
import com.runiverse.running_service.domain.common.vo.UserId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ChangeLiveRunningStatusHandler implements ChangeLiveRunningStatusUsecase {

    private final LiveRunningStatusChanger liveRunningStatusChanger;

    // DB를 보지 않는다 — 끝난 참가자의 늦은 PAUSE는 FINISHED가 막고, 기록은 좌표 판정이 지킨다.
    // Redis만 쓰므로 트랜잭션 경계도 두지 않는다
    @Override
    public void handle(ChangeLiveRunningStatusCommand command) {
        liveRunningStatusChanger.change(command.runningRoomId(), new UserId(command.userId()),
                command.targetDistanceMeters(), command.status());
    }
}
