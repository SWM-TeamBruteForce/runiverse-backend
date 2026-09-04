package com.runiverse.running_service.unit_test.match.application;

import com.github.f4b6a3.uuid.UuidCreator;
import com.runiverse.running_service.application.match.command.stream.OpenMatchStreamCommand;
import com.runiverse.running_service.application.match.command.stream.OpenMatchStreamHandler;
import com.runiverse.running_service.application.match.port.out.MatchStreamConnection;
import com.runiverse.running_service.application.match.port.out.MatchStreamPort;
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
@DisplayName("매칭 스트림 연결 단위 테스트")
class OpenMatchStreamHandlerTest {

    private static final UUID USER_ID = UuidCreator.getTimeOrderedEpoch();

    @Mock
    private MatchStreamPort matchStreamPort;

    @Mock
    private MatchStreamConnection newConnection;

    @Mock
    private MatchStreamConnection supersededConnection;

    @InjectMocks
    private OpenMatchStreamHandler openMatchStreamHandler;

    @Test
    @DisplayName("밀려난 이전 연결은 닫는다")
    void closesSupersededConnection() {
        // given -> 같은 유저가 옛 연결을 남긴 채 다시 붙은 상황
        given(matchStreamPort.register(new UserId(USER_ID), newConnection))
                .willReturn(Optional.of(supersededConnection));

        // when
        openMatchStreamHandler.handle(new OpenMatchStreamCommand(USER_ID, newConnection));

        // then
        verify(supersededConnection).closeSuperseded();
    }

    @Test
    @DisplayName("밀어낼 연결이 없으면 아무 연결도 닫지 않는다")
    void closesNothingWhenNoSupersededConnection() {
        // given
        given(matchStreamPort.register(new UserId(USER_ID), newConnection))
                .willReturn(Optional.empty());

        // when
        openMatchStreamHandler.handle(new OpenMatchStreamCommand(USER_ID, newConnection));

        // then
        verifyNoInteractions(supersededConnection);
    }
}
