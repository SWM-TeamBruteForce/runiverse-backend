package com.runiverse.running_service.unit_test.running.application;

import com.github.f4b6a3.uuid.UuidCreator;
import com.runiverse.running_service.application.running.command.session.RunningConnectionCloseListener;
import com.runiverse.running_service.application.running.command.session.RunningConnectionCloseRequestedEvent;
import com.runiverse.running_service.application.running.port.out.RunningConnection;
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
@DisplayName("탈퇴 시 러닝 연결 종료 단위 테스트")
class RunningConnectionCloseListenerTest {

    private static final UUID USER_ID = UuidCreator.getTimeOrderedEpoch();

    @Mock
    private RunningSessionPort runningSessionPort;

    @Mock
    private RunningConnection connection;

    @InjectMocks
    private RunningConnectionCloseListener listener;

    @Test
    @DisplayName("이 서버에 붙은 연결이 있으면 닫는다")
    void closesConnectionOfDeletedUser() {
        // given -> 열려 있는 소켓은 탈퇴 뒤에도 좌표를 받아 같은 방에 진행을 퍼뜨린다
        given(runningSessionPort.find(new UserId(USER_ID))).willReturn(Optional.of(connection));

        // when
        listener.close(new RunningConnectionCloseRequestedEvent(new UserId(USER_ID)));

        // then
        verify(connection).closeForAccountDeletion();
    }

    @Test
    @DisplayName("이 서버에 붙은 연결이 없으면 아무것도 하지 않는다")
    void doesNothingWithoutConnection() {
        // given -> 러닝 중이 아닌 탈퇴자 대부분이 이 경우다
        given(runningSessionPort.find(new UserId(USER_ID))).willReturn(Optional.empty());

        // when
        listener.close(new RunningConnectionCloseRequestedEvent(new UserId(USER_ID)));

        // then
        verifyNoInteractions(connection);
    }
}
