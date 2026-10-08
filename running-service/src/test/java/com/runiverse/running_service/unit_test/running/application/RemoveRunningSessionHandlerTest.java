package com.runiverse.running_service.unit_test.running.application;

import com.github.f4b6a3.uuid.UuidCreator;
import com.runiverse.running_service.application.running.command.session.RemoveRunningSessionCommand;
import com.runiverse.running_service.application.running.command.session.RemoveRunningSessionHandler;
import com.runiverse.running_service.application.running.common.LiveRunningStatusChanger;
import com.runiverse.running_service.application.running.port.out.LiveRunningStatus;
import com.runiverse.running_service.application.running.port.out.RunningConnection;
import com.runiverse.running_service.application.running.port.out.RunningRoomMembershipPort;
import com.runiverse.running_service.application.running.port.out.RunningSessionPort;
import com.runiverse.running_service.domain.common.vo.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@DisplayName("러닝 세션 해제 단위 테스트")
public class RemoveRunningSessionHandlerTest {

    private static final UUID USER_ID = UuidCreator.getTimeOrderedEpoch();
    private static final long ROOM_ID = 125L;
    private static final int TARGET_DISTANCE_METERS = 5_000;

    @Mock
    private RunningSessionPort runningSessionPort;

    @Mock
    private RunningRoomMembershipPort runningRoomMembershipPort;

    // 판정·발행·실패 처리는 LiveRunningStatusChangerTest가 본다 — 여기서는 언제 부르는지만 본다
    @Mock
    private LiveRunningStatusChanger liveRunningStatusChanger;

    @Mock
    private RunningConnection connection;

    @InjectMocks
    private RemoveRunningSessionHandler removeRunningSessionHandler;

    private RemoveRunningSessionCommand startedCommand() {
        return new RemoveRunningSessionCommand(USER_ID, connection, ROOM_ID, TARGET_DISTANCE_METERS);
    }

    @Test
    @DisplayName("내 연결이 실제로 빠지면 방에서도 나간다")
    void leavesRoomWhenConnectionRemoved() {
        // given
        given(runningSessionPort.remove(new UserId(USER_ID), connection)).willReturn(true);

        // when
        removeRunningSessionHandler.handle(startedCommand());

        // then
        verify(runningRoomMembershipPort).leave(new UserId(USER_ID));
    }

    @Test
    @DisplayName("내 연결이 실제로 빠지면 방의 목표 거리와 함께 DISCONNECTED로 바꾼다")
    void marksDisconnectedWhenConnectionRemoved() {
        // given
        given(runningSessionPort.remove(new UserId(USER_ID), connection)).willReturn(true);

        // when
        removeRunningSessionHandler.handle(startedCommand());

        // then -> 상대 화면에서 멈춘 것과 끊긴 것을 가른다
        verify(liveRunningStatusChanger).change(
                ROOM_ID, new UserId(USER_ID), TARGET_DISTANCE_METERS, LiveRunningStatus.DISCONNECTED);
    }

    @Test
    @DisplayName("목표 없는 솔로 방이면 목표 거리를 null로 넘긴다")
    void passesNullTargetForSoloRoom() {
        // given
        given(runningSessionPort.remove(new UserId(USER_ID), connection)).willReturn(true);

        // when
        removeRunningSessionHandler.handle(
                new RemoveRunningSessionCommand(USER_ID, connection, ROOM_ID, null));

        // then
        verify(liveRunningStatusChanger).change(
                ROOM_ID, new UserId(USER_ID), null, LiveRunningStatus.DISCONNECTED);
    }

    @Test
    @DisplayName("이미 새 연결이 자리를 가져갔으면 방에서 빼지도 끊김으로 바꾸지도 않는다")
    void keepsMembershipWhenConnectionAlreadySuperseded() {
        // given -> 밀려난 옛 연결이 뒤늦게 닫히는 상황
        given(runningSessionPort.remove(new UserId(USER_ID), connection)).willReturn(false);

        // when
        removeRunningSessionHandler.handle(startedCommand());

        // then -> 새 연결로 뛰는 사람이 상대 화면에서 끊긴 것으로 보이면 안 된다
        verifyNoInteractions(runningRoomMembershipPort, liveRunningStatusChanger);
    }

    @Test
    @DisplayName("RUNNING_START 전에 끊긴 연결은 상태를 건드리지 않는다")
    void skipsStatusWhenNeverStarted() {
        // given -> 세션에 방이 새겨지기 전이다. 알릴 상대도 없다
        given(runningSessionPort.remove(new UserId(USER_ID), connection)).willReturn(true);

        // when
        removeRunningSessionHandler.handle(
                new RemoveRunningSessionCommand(USER_ID, connection, null, null));

        // then
        verify(runningRoomMembershipPort).leave(new UserId(USER_ID));
        verifyNoInteractions(liveRunningStatusChanger);
    }
}
