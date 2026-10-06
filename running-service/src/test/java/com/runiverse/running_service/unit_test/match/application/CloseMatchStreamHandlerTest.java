package com.runiverse.running_service.unit_test.match.application;

import ch.qos.logback.classic.Level;
import com.github.f4b6a3.uuid.UuidCreator;
import com.runiverse.running_service.application.match.command.stream.CloseMatchStreamCommand;
import com.runiverse.running_service.application.match.command.stream.CloseMatchStreamHandler;
import com.runiverse.running_service.application.match.port.out.MatchRoomMembershipPort;
import com.runiverse.running_service.application.match.port.out.MatchStreamConnection;
import com.runiverse.running_service.application.match.port.out.MatchStreamPort;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.support.LogCapture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
@DisplayName("매칭 스트림 종료 단위 테스트")
class CloseMatchStreamHandlerTest {

    private static final UUID USER_ID = UuidCreator.getTimeOrderedEpoch();

    @Mock
    private MatchStreamPort matchStreamPort;

    @Mock
    private MatchRoomMembershipPort matchRoomMembershipPort;

    @Mock
    private MatchStreamConnection connection;

    private CloseMatchStreamHandler handler;
    private LogCapture log;

    @BeforeEach
    void setUp() {
        handler = new CloseMatchStreamHandler(matchStreamPort, matchRoomMembershipPort);
        log = LogCapture.of(CloseMatchStreamHandler.class);
    }

    @AfterEach
    void tearDown() {
        log.stop();
    }

    @Test
    @DisplayName("스트림 종료는 레지스트리 제거 여부를 영문 key로 담아 남긴다")
    void logsCloseWithParsableKeys() {
        // given -> 새 연결이 이미 자리를 가져간 경우라 지우지 않는다
        given(connection.id()).willReturn("conn-1");
        given(matchStreamPort.remove(new UserId(USER_ID), connection)).willReturn(false);

        // when
        handler.handle(new CloseMatchStreamCommand(USER_ID, connection));

        // then -> 한글 key는 수집 단계의 필드 이름으로 쓰기 어렵다
        assertThat(log.messages(Level.INFO)).containsExactly(
                "[매칭] 스트림 종료 성공 - userId=" + USER_ID + ", connectionId=conn-1, removed=false");
    }
}
