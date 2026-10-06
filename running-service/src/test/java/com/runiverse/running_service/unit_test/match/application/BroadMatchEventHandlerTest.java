package com.runiverse.running_service.unit_test.match.application;

import com.github.f4b6a3.uuid.UuidCreator;
import com.runiverse.running_service.application.match.command.broadcast.BroadMatchEventHandler;
import com.runiverse.running_service.application.match.command.broadcast.BroadcastMatchEventCommand;
import com.runiverse.running_service.application.match.port.out.MatchStreamConnection;
import com.runiverse.running_service.application.match.port.out.MatchStreamEvent;
import com.runiverse.running_service.application.match.port.out.MatchStreamPort;
import com.runiverse.running_service.application.match.port.out.LoadMatchRoomMembersPort;
import com.runiverse.running_service.application.match.port.out.RoomInfo;
import com.runiverse.running_service.application.match.port.out.RunningReady;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.running.room.vo.RunningRoomStatus;
import ch.qos.logback.classic.Level;
import com.runiverse.running_service.support.LogCapture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("매칭 이벤트 전파 단위 테스트")
class BroadMatchEventHandlerTest {

    private static final long ROOM_ID = 125L;
    private static final UserId USER_A = new UserId(UuidCreator.getTimeOrderedEpoch());
    private static final UserId USER_B = new UserId(UuidCreator.getTimeOrderedEpoch());
    private static final RoomInfo ROOM_INFO = new RoomInfo(
            ROOM_ID, RunningRoomStatus.MATCHED, LocalDateTime.now().plusMinutes(10),
            LocalDateTime.now(), 5_000, 360, List.of());

    @Mock
    private LoadMatchRoomMembersPort loadMatchRoomMembersPort;

    @Mock
    private MatchStreamPort matchStreamPort;

    @Mock
    private MatchStreamConnection connectionA;

    @Mock
    private MatchStreamConnection connectionB;

    private BroadMatchEventHandler broadMatchEventHandler;

    @BeforeEach
    void setUp() {
        broadMatchEventHandler = new BroadMatchEventHandler(
                loadMatchRoomMembersPort, matchStreamPort);
    }

    @Test
    @DisplayName("RoomInfo를 싣지 않는 이벤트도 수신자를 찾는다")
    void findsMembersForEventWithoutRoomInfo() {
        // given -> RUNNING_READY는 room이 null이다. 방 ID를 RoomInfo에서 꺼내면 여기서 터진다
        MatchStreamEvent event = MatchStreamEvent.runningReady(
                new RunningReady(ROOM_ID, LocalDateTime.now().plusSeconds(10), 10_000));
        givenMembers(USER_A);

        // when
        broadMatchEventHandler.handle(new BroadcastMatchEventCommand(event));

        // then -> 수신자는 RoomInfo.players가 아니라 DB에서 다시 읽는다
        verify(connectionA).send(event);
    }

    @Test
    @DisplayName("이 인스턴스에 붙은 참가자 전원에게 보낸다")
    void sendsToEveryConnectedMember() {
        // given
        MatchStreamEvent event = MatchStreamEvent.updated(ROOM_INFO);
        givenMembers(USER_A, USER_B);

        // when
        broadMatchEventHandler.handle(new BroadcastMatchEventCommand(event));

        // then
        verify(connectionA).send(event);
        verify(connectionB).send(event);
    }

    @Test
    @DisplayName("다른 서버에 붙은 참가자는 건너뛴다")
    void skipsMemberConnectedElsewhere() {
        // given -> 그쪽 인스턴스가 같은 Redis 메시지를 받아 자기 몫을 보낸다
        MatchStreamEvent event = MatchStreamEvent.updated(ROOM_INFO);
        given(loadMatchRoomMembersPort.usersIn(ROOM_ID)).willReturn(Set.of(USER_A, USER_B));
        given(matchStreamPort.find(USER_A)).willReturn(Optional.of(connectionA));
        given(matchStreamPort.find(USER_B)).willReturn(Optional.empty());

        // when
        broadMatchEventHandler.handle(new BroadcastMatchEventCommand(event));

        // then
        verify(connectionA).send(event);
    }

    @Test
    @DisplayName("한 연결이 실패해도 나머지에게는 보낸다")
    void oneFailureDoesNotBlockOthers() {
        // given -> 끊긴 단말은 스스로 정리된다. 그 실패가 남은 수신자를 막으면 안 된다
        MatchStreamEvent event = MatchStreamEvent.updated(ROOM_INFO);
        givenMembers(USER_A, USER_B);
        doThrow(new IllegalStateException("끊긴 단말")).when(connectionA).send(event);
        given(connectionA.id()).willReturn("conn-a");

        // when
        broadMatchEventHandler.handle(new BroadcastMatchEventCommand(event));

        // then
        verify(connectionB).send(event);
    }

    private void givenMembers(UserId... users) {
        given(loadMatchRoomMembersPort.usersIn(ROOM_ID)).willReturn(Set.of(users));
        if (users.length > 0) {
            given(matchStreamPort.find(USER_A)).willReturn(Optional.of(connectionA));
        }
        if (users.length > 1) {
            given(matchStreamPort.find(USER_B)).willReturn(Optional.of(connectionB));
        }
    }

    @Test
    @DisplayName("연결이 스스로 처리하지 못한 전송 실패는 연결 ID와 원인 예외를 담아 ERROR로 남긴다")
    void logsUnexpectedSendFailureAsError() {
        // given -> 끊긴 단말은 연결이 안에서 닫는다. 여기까지 올라온 예외는 예상 밖이다
        LogCapture log = LogCapture.of(BroadMatchEventHandler.class);
        MatchStreamEvent event = MatchStreamEvent.updated(ROOM_INFO);
        givenMembers(USER_A);
        doThrow(new IllegalStateException("전송 실패")).when(connectionA).send(event);
        given(connectionA.id()).willReturn("conn-a");

        try {
            // when
            broadMatchEventHandler.handle(new BroadcastMatchEventCommand(event));

            // then
            assertThat(log.messages(Level.ERROR))
                    .containsExactly("[매칭] 이벤트 전송 실패: 처리하지 못한 예외 - connectionId=conn-a");
            assertThat(log.events(Level.ERROR).getFirst().getThrowableProxy()).isNotNull();
        } finally {
            log.stop();
        }
    }
}
