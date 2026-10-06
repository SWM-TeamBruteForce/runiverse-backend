package com.runiverse.running_service.unit_test.running.application;

import com.github.f4b6a3.uuid.UuidCreator;
import com.runiverse.running_service.application.running.command.session.RegisterRunningSessionCommand;
import com.runiverse.running_service.application.running.command.session.RegisterRunningSessionHandler;
import com.runiverse.running_service.application.running.port.out.PublishSupersedePort;
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

import java.util.Optional;
import java.util.UUID;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@DisplayName("러닝 세션 등록 단위 테스트")
public class RegisterRunningSessionHandlerTest {

    private static final UUID USER_ID = UuidCreator.getTimeOrderedEpoch();
    private static final long ROOM_ID = 125L;
    private static final String NEW_SESSION_ID = "sess-new";

    @Mock
    private RunningSessionPort runningSessionPort;

    @Mock
    private RunningRoomMembershipPort runningRoomMembershipPort;

    @Mock
    private PublishSupersedePort publishSupersedePort;

    @Mock
    private RunningConnection newConnection;

    @Mock
    private RunningConnection supersededConnection;

    @InjectMocks
    private RegisterRunningSessionHandler registerRunningSessionHandler;

    @Test
    @DisplayName("같은 인스턴스에 남아 있던 연결은 통지를 기다리지 않고 즉시 닫는다")
    void closesSupersededConnectionOnSameInstance() {
        // given -> 같은 유저가 같은 인스턴스로 다시 붙은 상황
        given(newConnection.id()).willReturn(NEW_SESSION_ID);
        given(runningSessionPort.register(new UserId(USER_ID), newConnection))
                .willReturn(Optional.of(supersededConnection));

        // when
        registerRunningSessionHandler.handle(
                new RegisterRunningSessionCommand(USER_ID, ROOM_ID, newConnection));

        // then -> 발행보다 먼저 방에 합류해야 자기 통지를 되받는다
        verify(supersededConnection).closeSuperseded();
        verify(runningRoomMembershipPort).join(new UserId(USER_ID), ROOM_ID);
        verify(publishSupersedePort).publish(USER_ID, ROOM_ID, NEW_SESSION_ID);
    }

    @Test
    @DisplayName("이 인스턴스에 밀어낼 연결이 없어도 다른 인스턴스에는 통지한다")
    void publishesEvenWhenNothingSupersededLocally() {
        // given -> 옛 연결이 다른 인스턴스에 남아 있을 수 있다
        given(newConnection.id()).willReturn(NEW_SESSION_ID);
        given(runningSessionPort.register(new UserId(USER_ID), newConnection))
                .willReturn(Optional.empty());

        // when
        registerRunningSessionHandler.handle(
                new RegisterRunningSessionCommand(USER_ID, ROOM_ID, newConnection));

        // then
        verify(publishSupersedePort).publish(USER_ID, ROOM_ID, NEW_SESSION_ID);
        verifyNoInteractions(supersededConnection);
    }
}
