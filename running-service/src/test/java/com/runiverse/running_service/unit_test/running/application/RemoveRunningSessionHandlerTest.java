package com.runiverse.running_service.unit_test.running.application;

import com.github.f4b6a3.uuid.UuidCreator;
import com.runiverse.running_service.application.running.command.session.RemoveRunningSessionCommand;
import com.runiverse.running_service.application.running.command.session.RemoveRunningSessionHandler;
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

    @Mock
    private RunningSessionPort runningSessionPort;

    @Mock
    private RunningRoomMembershipPort runningRoomMembershipPort;

    @Mock
    private RunningConnection connection;

    @InjectMocks
    private RemoveRunningSessionHandler removeRunningSessionHandler;

    @Test
    @DisplayName("내 연결이 실제로 빠지면 방에서도 나간다")
    void leavesRoomWhenConnectionRemoved() {
        // given
        given(runningSessionPort.remove(new UserId(USER_ID), connection)).willReturn(true);

        // when
        removeRunningSessionHandler.handle(new RemoveRunningSessionCommand(USER_ID, connection));

        // then
        verify(runningRoomMembershipPort).leave(new UserId(USER_ID));
    }

    @Test
    @DisplayName("이미 새 연결이 자리를 가져갔으면 방에서 빼지 않는다")
    void keepsMembershipWhenConnectionAlreadySuperseded() {
        // given -> 밀려난 옛 연결이 뒤늦게 닫히는 상황
        given(runningSessionPort.remove(new UserId(USER_ID), connection)).willReturn(false);

        // when
        removeRunningSessionHandler.handle(new RemoveRunningSessionCommand(USER_ID, connection));

        // then
        verifyNoInteractions(runningRoomMembershipPort);
    }
}
