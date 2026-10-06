package com.runiverse.running_service.unit_test.running.application;

import com.github.f4b6a3.uuid.UuidCreator;
import com.runiverse.running_service.application.running.command.session.CloseSupersededSessionCommand;
import com.runiverse.running_service.application.running.command.session.CloseSupersededSessionHandler;
import com.runiverse.running_service.application.running.port.out.RunningConnection;
import com.runiverse.running_service.application.running.port.out.RunningSessionPort;
import com.runiverse.running_service.domain.common.vo.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@DisplayName("밀어내기 통지 처리 단위 테스트")
public class CloseSupersededSessionHandlerTest {

    private static final UUID USER_ID = UuidCreator.getTimeOrderedEpoch();
    private static final String WINNER_SESSION_ID = "sess-ccc";
    private static final String SUPERSEDED_SESSION_ID = "sess-aaa";

    @Mock
    private RunningSessionPort runningSessionPort;

    @Mock
    private RunningConnection connection;

    @InjectMocks
    private CloseSupersededSessionHandler closeSupersededSessionHandler;

    @Nested
    @DisplayName("이 인스턴스가 그 유저의 연결을 들고 있으면")
    class WhenConnectionIsHeld {

        @Test
        @DisplayName("승자가 아닌 연결은 닫는다")
        void closesConnectionThatIsNotWinner() {
            // given -> 유저가 다른 인스턴스로 재접속해 밀려난 옛 연결을 들고 있는 서버
            given(runningSessionPort.find(new UserId(USER_ID))).willReturn(Optional.of(connection));
            given(connection.id()).willReturn(SUPERSEDED_SESSION_ID);

            // when
            closeSupersededSessionHandler.handle(
                    new CloseSupersededSessionCommand(USER_ID, WINNER_SESSION_ID));

            // then
            verify(connection).closeSuperseded();
        }

        @Test
        @DisplayName("승자 연결은 닫지 않는다 — 발행한 인스턴스도 자기 통지를 되받는다")
        void keepsWinnerConnection() {
            // given -> 방금 새 연결을 받아 통지를 발행한 서버
            given(runningSessionPort.find(new UserId(USER_ID))).willReturn(Optional.of(connection));
            given(connection.id()).willReturn(WINNER_SESSION_ID);

            // when
            closeSupersededSessionHandler.handle(
                    new CloseSupersededSessionCommand(USER_ID, WINNER_SESSION_ID));

            // then
            verify(connection, never()).closeSuperseded();
        }
    }

    @Test
    @DisplayName("그 유저를 들고 있지 않은 인스턴스는 아무것도 하지 않는다")
    void doesNothingWhenConnectionIsNotHeld() {
        // given -> 통지와 무관한 서버
        given(runningSessionPort.find(new UserId(USER_ID))).willReturn(Optional.empty());

        // when
        closeSupersededSessionHandler.handle(
                new CloseSupersededSessionCommand(USER_ID, WINNER_SESSION_ID));

        // then
        verifyNoInteractions(connection);
    }
}
