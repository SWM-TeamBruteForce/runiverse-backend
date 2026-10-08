package com.runiverse.running_service.unit_test.running.application;

import com.github.f4b6a3.uuid.UuidCreator;
import com.runiverse.running_service.application.running.command.status.ChangeLiveRunningStatusCommand;
import com.runiverse.running_service.application.running.command.status.ChangeLiveRunningStatusHandler;
import com.runiverse.running_service.application.running.common.LiveRunningStatusChanger;
import com.runiverse.running_service.application.running.port.out.LiveRunningStatus;
import com.runiverse.running_service.domain.common.vo.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("러닝 화면 참가자 상태 변경 유스케이스 단위 테스트")
class ChangeLiveRunningStatusHandlerTest {

    private static final long ROOM_ID = 125L;
    private static final UUID USER_ID = UuidCreator.getTimeOrderedEpoch();

    // 판정·발행·실패 처리는 LiveRunningStatusChangerTest가 본다 — 여기서는 그대로 넘기는지만 본다
    @Mock
    private LiveRunningStatusChanger liveRunningStatusChanger;

    @InjectMocks
    private ChangeLiveRunningStatusHandler handler;

    @Test
    @DisplayName("방·참가자·목표 거리·상태를 그대로 넘긴다")
    void delegatesToChanger() {
        // when
        handler.handle(new ChangeLiveRunningStatusCommand(
                USER_ID, ROOM_ID, 5_000, LiveRunningStatus.PAUSED));

        // then
        verify(liveRunningStatusChanger).change(
                ROOM_ID, new UserId(USER_ID), 5_000, LiveRunningStatus.PAUSED);
    }

    @Test
    @DisplayName("목표 없는 솔로 방이면 목표 거리를 null로 넘긴다")
    void delegatesNullTarget() {
        // when
        handler.handle(new ChangeLiveRunningStatusCommand(
                USER_ID, ROOM_ID, null, LiveRunningStatus.RUNNING));

        // then
        verify(liveRunningStatusChanger).change(
                ROOM_ID, new UserId(USER_ID), null, LiveRunningStatus.RUNNING);
    }
}
