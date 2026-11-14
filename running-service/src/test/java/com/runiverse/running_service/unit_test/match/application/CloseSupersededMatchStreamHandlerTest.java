package com.runiverse.running_service.unit_test.match.application;

import ch.qos.logback.classic.Level;
import com.github.f4b6a3.uuid.UuidCreator;
import com.runiverse.running_service.application.match.command.stream.CloseSupersededMatchStreamCommand;
import com.runiverse.running_service.application.match.command.stream.CloseSupersededMatchStreamHandler;
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
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

// 다른 인스턴스에서 새 매칭 스트림이 열렸다는 알림을 받은 인스턴스가 자기 옛 연결만 정리하는지 본다
// (architecture.md 「다중 인스턴스」 2번)
@ExtendWith(MockitoExtension.class)
@DisplayName("다른 인스턴스 연결로 대체된 매칭 스트림 정리 단위 테스트")
class CloseSupersededMatchStreamHandlerTest {

    private static final UUID USER_ID = UuidCreator.getTimeOrderedEpoch();
    private static final UserId USER = new UserId(USER_ID);
    private static final String OLD_CONNECTION = "conn-old";
    private static final String WINNER_CONNECTION = "conn-winner";

    @Mock
    private MatchStreamPort matchStreamPort;

    @Mock
    private MatchRoomMembershipPort matchRoomMembershipPort;

    @Mock
    private MatchStreamConnection connection;

    private CloseSupersededMatchStreamHandler handler;
    private LogCapture log;

    @BeforeEach
    void setUp() {
        handler = new CloseSupersededMatchStreamHandler(matchStreamPort, matchRoomMembershipPort);
        log = LogCapture.of(CloseSupersededMatchStreamHandler.class);
    }

    @AfterEach
    void tearDown() {
        log.stop();
    }

    private void handle() {
        handler.handle(new CloseSupersededMatchStreamCommand(USER_ID, WINNER_CONNECTION));
    }

    @Test
    @DisplayName("이 인스턴스의 옛 연결이면 연결표·명부에서 먼저 빼고 닫는다")
    void removesThenLeavesThenCloses() {
        // given
        given(connection.id()).willReturn(OLD_CONNECTION);
        given(matchStreamPort.find(USER)).willReturn(Optional.of(connection));
        given(matchStreamPort.remove(USER, connection)).willReturn(true);

        // when
        handle();

        // then -> 닫기 전에 빼야 늦게 도는 종료 처리가 가드에 걸려 아무것도 하지 않는다
        InOrder order = inOrder(matchStreamPort, matchRoomMembershipPort, connection);
        order.verify(matchStreamPort).remove(USER, connection);
        order.verify(matchRoomMembershipPort).leave(USER);
        order.verify(connection).closeSuperseded();
    }

    @Test
    @DisplayName("자기가 보낸 알림을 되받아 이긴 연결을 찾으면 아무것도 하지 않는다")
    void keepsWinnerConnection() {
        // given -> 알림을 보낸 인스턴스도 공용 채널을 듣는다
        given(connection.id()).willReturn(WINNER_CONNECTION);
        given(matchStreamPort.find(USER)).willReturn(Optional.of(connection));

        // when
        handle();

        // then
        verify(matchStreamPort, never()).remove(any(), any());
        verify(matchRoomMembershipPort, never()).leave(any());
        verify(connection, never()).closeSuperseded();
    }

    @Test
    @DisplayName("이 인스턴스에 그 사용자의 연결이 없으면 아무것도 하지 않는다")
    void ignoresUserNotOnThisInstance() {
        // given -> 대부분의 인스턴스가 이 경우다
        given(matchStreamPort.find(USER)).willReturn(Optional.empty());

        // when
        handle();

        // then -> 명부를 건드리면 다른 경로로 든 기록까지 지울 수 있다
        verify(matchStreamPort, never()).remove(any(), any());
        verify(matchRoomMembershipPort, never()).leave(any());
    }

    @Test
    @DisplayName("찾은 뒤 이 인스턴스에 더 새 연결이 붙어 자리를 가져갔으면 손대지 않는다")
    void skipsWhenNewerLocalConnectionTookOver() {
        // given -> find와 remove 사이에 register가 돌았다. 찾은 연결은 그 register가 이미 닫았다
        given(connection.id()).willReturn(OLD_CONNECTION);
        given(matchStreamPort.find(USER)).willReturn(Optional.of(connection));
        given(matchStreamPort.remove(USER, connection)).willReturn(false);

        // when
        handle();

        // then -> 여기서 leave하면 더 새 연결의 방 명부를 지운다
        verify(matchRoomMembershipPort, never()).leave(any());
        verify(connection, never()).closeSuperseded();
    }

    @Test
    @DisplayName("대체하면 옛 연결과 이긴 연결 ID를 영문 key로 담아 남긴다")
    void logsSupersedeWithParsableKeys() {
        // given
        given(connection.id()).willReturn(OLD_CONNECTION);
        given(matchStreamPort.find(USER)).willReturn(Optional.of(connection));
        given(matchStreamPort.remove(USER, connection)).willReturn(true);

        // when
        handle();

        // then
        assertThat(log.messages(Level.INFO)).containsExactly(
                "[매칭] 다른 인스턴스 연결로 대체 성공 - userId=" + USER_ID
                        + ", connectionId=" + OLD_CONNECTION + ", winnerConnectionId=" + WINNER_CONNECTION);
    }
}
