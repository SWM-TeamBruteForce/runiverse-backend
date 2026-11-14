package com.runiverse.running_service.unit_test.match.application;

import com.github.f4b6a3.uuid.UuidCreator;
import com.runiverse.running_service.application.match.command.stream.OpenMatchStreamCommand;
import com.runiverse.running_service.application.match.command.stream.OpenMatchStreamHandler;
import com.runiverse.running_service.application.match.common.RoomInfoAssembler;
import com.runiverse.running_service.application.match.exception.ActiveMatchNotFoundException;
import com.runiverse.running_service.application.match.exception.MatchStreamUnavailableException;
import com.runiverse.running_service.application.match.port.out.MatchEventType;
import com.runiverse.running_service.application.match.port.out.MatchRoomMembershipPort;
import com.runiverse.running_service.application.match.port.out.MatchStreamConnection;
import com.runiverse.running_service.application.match.port.out.MatchStreamEvent;
import com.runiverse.running_service.application.match.port.out.MatchStreamPort;
import com.runiverse.running_service.application.match.port.out.PublishMatchStreamSupersedePort;
import com.runiverse.running_service.application.match.port.out.RoomInfo;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.running.room.vo.RunningRoomStatus;
import com.runiverse.running_service.domain.running.room.vo.RunningRoomType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@DisplayName("매칭 스트림 연결 단위 테스트")
class OpenMatchStreamHandlerTest {

    private static final UUID USER_ID = UuidCreator.getTimeOrderedEpoch();
    private static final long ROOM_ID = 125L;
    private static final RoomInfo ROOM_INFO = new RoomInfo(
            ROOM_ID, RunningRoomType.MATCH, RunningRoomStatus.MATCHING, LocalDateTime.now().plusHours(2),
            LocalDateTime.now().plusHours(1), 5_000, 360, List.of(), List.of());

    @Mock
    private MatchStreamPort matchStreamPort;

    @Mock
    private MatchStreamConnection newConnection;

    @Mock
    private MatchStreamConnection supersededConnection;

    @Mock
    private MatchRoomMembershipPort matchRoomMembershipPort;

    @Mock
    private RoomInfoAssembler roomInfoAssembler;

    @Mock
    private PublishMatchStreamSupersedePort publishMatchStreamSupersedePort;

    @InjectMocks
    private OpenMatchStreamHandler openMatchStreamHandler;

    @Test
    @DisplayName("방 채널을 구독하고 스냅샷을 보낸다")
    void subscribesAndSendsSnapshot() {
        // given -> 재연결이 곧 스냅샷 재수신이다
        givenActiveMatch();
        given(matchStreamPort.register(new UserId(USER_ID), newConnection))
                .willReturn(Optional.empty());

        // when
        openMatchStreamHandler.handle(new OpenMatchStreamCommand(USER_ID, newConnection));

        // then -> 구독이 먼저다. 스냅샷을 보내는 사이에 온 갱신을 놓치지 않는다
        InOrder order = inOrder(matchRoomMembershipPort, newConnection);
        order.verify(matchRoomMembershipPort).join(new UserId(USER_ID), ROOM_ID);
        order.verify(newConnection)
                .send(new MatchStreamEvent(MatchEventType.MATCH_ROOM_ENTRANCE, ROOM_ID, ROOM_INFO, null, null, null, null));
    }

    @Test
    @DisplayName("초대 방이면 시작까지 남은 시간을 실은 입장 스냅샷을 보낸다")
    void sendsEntranceSnapshotForInviteRoom() {
        // given -> 시작 1시간 전 방이다
        RoomInfo inviteRoom = new RoomInfo(
                ROOM_ID, RunningRoomType.INVITE, RunningRoomStatus.MATCHING, LocalDateTime.now().plusHours(1),
                LocalDateTime.now().plusHours(1).minusMinutes(1), 7_000, 360, List.of(), List.of());
        given(roomInfoAssembler.assembleFor(new UserId(USER_ID))).willReturn(Optional.of(inviteRoom));
        given(matchStreamPort.register(new UserId(USER_ID), newConnection)).willReturn(Optional.empty());

        // when
        openMatchStreamHandler.handle(new OpenMatchStreamCommand(USER_ID, newConnection));

        // then -> 매칭 방의 MATCH_ROOM_ENTRANCE 대신 INVITE_ROOM_ENTRANCE다. 남은 시간은 보낸 시점 기준이다
        ArgumentCaptor<MatchStreamEvent> sent = ArgumentCaptor.forClass(MatchStreamEvent.class);
        verify(newConnection).send(sent.capture());
        assertThat(sent.getValue().type()).isEqualTo(MatchEventType.INVITE_ROOM_ENTRANCE);
        assertThat(sent.getValue().room()).isEqualTo(inviteRoom);
        assertThat(sent.getValue().startsInMs()).isBetween(
                Duration.ofMinutes(59).toMillis(), Duration.ofHours(1).toMillis());
    }

    @Test
    @DisplayName("밀려난 이전 연결은 닫는다")
    void closesSupersededConnection() {
        // given -> 앱 재시작처럼 끊긴 줄 모르고 남아 있는 옛 연결이 있다
        givenActiveMatch();
        given(matchStreamPort.register(new UserId(USER_ID), newConnection))
                .willReturn(Optional.of(supersededConnection));

        // when
        openMatchStreamHandler.handle(new OpenMatchStreamCommand(USER_ID, newConnection));

        // then -> 마지막 연결만 남긴다
        verify(supersededConnection).closeSuperseded();
    }

    @Test
    @DisplayName("밀어낼 연결이 없으면 아무 연결도 닫지 않는다")
    void closesNothingWhenNoSupersededConnection() {
        // given
        givenActiveMatch();
        given(matchStreamPort.register(new UserId(USER_ID), newConnection))
                .willReturn(Optional.empty());

        // when
        openMatchStreamHandler.handle(new OpenMatchStreamCommand(USER_ID, newConnection));

        // then
        verifyNoInteractions(supersededConnection);
    }

    @Test
    @DisplayName("활성 신청이 없으면 연결을 거절한다")
    void rejectsWhenNoActiveApplication() {
        // given -> 열어주면 붙을 방이 없어 어느 채널도 구독하지 못하고,
        //          그 뒤에 신청해도 이벤트가 영영 오지 않는 연결이 된다
        given(roomInfoAssembler.assembleFor(new UserId(USER_ID))).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() ->
                openMatchStreamHandler.handle(new OpenMatchStreamCommand(USER_ID, newConnection)))
                .isInstanceOf(ActiveMatchNotFoundException.class);
    }

    @Test
    @DisplayName("거절한 연결은 레지스트리에 흔적을 남기지 않는다")
    void leavesNoTraceWhenRejected() {
        // given -> 등록 전에 검사해야 정리해야 할 것이 생기지 않는다
        given(roomInfoAssembler.assembleFor(new UserId(USER_ID))).willReturn(Optional.empty());

        // when
        assertThatThrownBy(() ->
                openMatchStreamHandler.handle(new OpenMatchStreamCommand(USER_ID, newConnection)))
                .isInstanceOf(ActiveMatchNotFoundException.class);

        // then
        verifyNoInteractions(matchStreamPort, matchRoomMembershipPort, newConnection);
        // then -> 실패한 연결이 다른 인스턴스의 정상 연결을 닫으면 안 된다
        verifyNoInteractions(publishMatchStreamSupersedePort);
    }

    @Test
    @DisplayName("등록·구독 뒤, 스냅샷 전에 다른 인스턴스에 대체를 알린다")
    void publishesSupersedeAfterRegisterAndJoin() {
        // given
        givenActiveMatch();
        given(newConnection.id()).willReturn("conn-new");
        given(matchStreamPort.register(new UserId(USER_ID), newConnection)).willReturn(Optional.empty());

        // when
        openMatchStreamHandler.handle(new OpenMatchStreamCommand(USER_ID, newConnection));

        // then -> 이 인스턴스도 이 알림을 되받는다. 등록이 먼저여야 되받았을 때 새 연결을 이긴 연결로 알아본다
        InOrder order = inOrder(matchStreamPort, matchRoomMembershipPort, publishMatchStreamSupersedePort, newConnection);
        order.verify(matchStreamPort).register(new UserId(USER_ID), newConnection);
        order.verify(matchRoomMembershipPort).join(new UserId(USER_ID), ROOM_ID);
        order.verify(publishMatchStreamSupersedePort).publish(new UserId(USER_ID), "conn-new");
        order.verify(newConnection).send(any());
    }

    @Test
    @DisplayName("같은 인스턴스의 옛 연결을 닫았어도 다른 인스턴스에 대체를 알린다")
    void publishesSupersedeEvenWhenLocalConnectionWasReplaced() {
        // given -> 이 인스턴스에서 하나를 닫았다고 다른 인스턴스에 옛 연결이 없다는 보장은 없다
        givenActiveMatch();
        given(newConnection.id()).willReturn("conn-new");
        given(matchStreamPort.register(new UserId(USER_ID), newConnection))
                .willReturn(Optional.of(supersededConnection));

        // when
        openMatchStreamHandler.handle(new OpenMatchStreamCommand(USER_ID, newConnection));

        // then
        verify(supersededConnection).closeSuperseded();
        verify(publishMatchStreamSupersedePort).publish(new UserId(USER_ID), "conn-new");
        verify(supersededConnection, never()).send(any());
    }

    @Test
    @DisplayName("방 구독에 실패하면 등록한 연결을 연결표에서 빼고 예외를 그대로 알린다")
    void removesConnectionWhenJoinFails() {
        // given
        givenActiveMatch();
        given(matchStreamPort.register(new UserId(USER_ID), newConnection)).willReturn(Optional.empty());
        willThrow(new MatchStreamUnavailableException())
                .given(matchRoomMembershipPort).join(new UserId(USER_ID), ROOM_ID);

        // when
        assertThatThrownBy(() ->
                openMatchStreamHandler.handle(new OpenMatchStreamCommand(USER_ID, newConnection)))
                .isInstanceOf(MatchStreamUnavailableException.class);

        // then -> 응답이 에러로 나가 연결 종료 콜백이 오지 않는다. 남기면 이벤트가 닿지 않는 연결에 쌓인다
        verify(matchStreamPort).remove(new UserId(USER_ID), newConnection);
    }

    @Test
    @DisplayName("방 구독에 실패한 연결은 다른 인스턴스에 대체를 알리지 않고 스냅샷도 보내지 않는다")
    void sendsNothingWhenJoinFails() {
        // given
        givenActiveMatch();
        given(matchStreamPort.register(new UserId(USER_ID), newConnection)).willReturn(Optional.empty());
        willThrow(new MatchStreamUnavailableException())
                .given(matchRoomMembershipPort).join(new UserId(USER_ID), ROOM_ID);

        // when
        assertThatThrownBy(() ->
                openMatchStreamHandler.handle(new OpenMatchStreamCommand(USER_ID, newConnection)))
                .isInstanceOf(MatchStreamUnavailableException.class);

        // then -> 실패한 연결이 다른 인스턴스의 정상 연결을 닫으면 안 된다
        verifyNoInteractions(publishMatchStreamSupersedePort);
        verify(newConnection, never()).send(any());
    }

    private void givenActiveMatch() {
        given(roomInfoAssembler.assembleFor(new UserId(USER_ID)))
                .willReturn(Optional.of(ROOM_INFO));
    }
}
