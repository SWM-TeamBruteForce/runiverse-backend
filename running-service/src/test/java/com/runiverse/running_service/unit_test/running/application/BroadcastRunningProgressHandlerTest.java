package com.runiverse.running_service.unit_test.running.application;

import com.github.f4b6a3.uuid.UuidCreator;
import com.runiverse.running_service.application.running.command.progress.BroadcastRunningProgressCommand;
import com.runiverse.running_service.application.running.command.progress.BroadcastRunningProgressHandler;
import com.runiverse.running_service.application.running.port.out.LoadRunningRoomMembersPort;
import com.runiverse.running_service.application.running.port.out.RunningConnection;
import com.runiverse.running_service.application.running.port.out.RunningProgress;
import com.runiverse.running_service.application.running.port.out.RunningSessionPort;
import com.runiverse.running_service.domain.common.vo.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@DisplayName("러닝 진행 브로드캐스트 단위 테스트")
class BroadcastRunningProgressHandlerTest {

    private static final long ROOM_ID = 125L;
    private static final UUID SENDER_ID = UuidCreator.getTimeOrderedEpoch();
    private static final UUID OTHER_ID = UuidCreator.getTimeOrderedEpoch();
    private static final UUID THIRD_ID = UuidCreator.getTimeOrderedEpoch();

    @Mock
    private LoadRunningRoomMembersPort loadRunningRoomMembersPort;

    @Mock
    private RunningSessionPort runningSessionPort;

    // 보낸 사람 본인의 소켓 — 이제 자기 진행도 돌려받는다
    @Mock
    private RunningConnection senderConnection;

    @Mock
    private RunningConnection otherConnection;

    @Mock
    private RunningConnection thirdConnection;

    @InjectMocks
    private BroadcastRunningProgressHandler broadcastRunningProgressHandler;

    private static final RunningProgress PROGRESS =
            new RunningProgress(SENDER_ID, 1_520, 5_000, 345, false);

    private void handle() {
        broadcastRunningProgressHandler.handle(
                new BroadcastRunningProgressCommand(ROOM_ID, PROGRESS));
    }

    @Test
    @DisplayName("같은 방의 참가자 전원에게 진행 정보를 보낸다")
    void sendsToEveryMember() {
        // given
        given(loadRunningRoomMembersPort.usersIn(ROOM_ID))
                .willReturn(Set.of(new UserId(SENDER_ID), new UserId(OTHER_ID),
                        new UserId(THIRD_ID)));
        given(runningSessionPort.find(new UserId(SENDER_ID)))
                .willReturn(Optional.of(senderConnection));
        given(runningSessionPort.find(new UserId(OTHER_ID)))
                .willReturn(Optional.of(otherConnection));
        given(runningSessionPort.find(new UserId(THIRD_ID)))
                .willReturn(Optional.of(thirdConnection));

        // when
        handle();

        // then -> 방 전체가 같은 서버 기준값으로 같은 화면을 그린다
        verify(senderConnection).sendProgress(PROGRESS);
        verify(otherConnection).sendProgress(PROGRESS);
        verify(thirdConnection).sendProgress(PROGRESS);
    }

    @Test
    @DisplayName("보낸 사람 본인도 받는다")
    void sendsBackToSender() {
        // given -> 콤보 통지와 수신자 규칙을 맞춘다. 클라가 분기 없이 한 벌로 처리한다.
        // 다만 본인 표시 거리는 로컬 계산값이 정본이다 — 이 값으로 덮으면
        // 10초마다 서버 누적으로 숫자가 뒤로 튄다
        given(loadRunningRoomMembersPort.usersIn(ROOM_ID))
                .willReturn(Set.of(new UserId(SENDER_ID)));
        given(runningSessionPort.find(new UserId(SENDER_ID)))
                .willReturn(Optional.of(senderConnection));

        // when
        handle();

        // then
        verify(senderConnection).sendProgress(PROGRESS);
        verifyNoInteractions(otherConnection);
    }

    @Test
    @DisplayName("다른 인스턴스에 붙은 참가자는 건너뛴다")
    void skipsMembersConnectedElsewhere() {
        // given -> 명부에는 있지만 이 인스턴스에 소켓이 없다.
        // 그쪽 인스턴스가 같은 메시지를 받아 자기 몫을 보낸다
        given(loadRunningRoomMembersPort.usersIn(ROOM_ID))
                .willReturn(Set.of(new UserId(SENDER_ID), new UserId(OTHER_ID)));
        given(runningSessionPort.find(new UserId(SENDER_ID)))
                .willReturn(Optional.of(senderConnection));
        given(runningSessionPort.find(new UserId(OTHER_ID))).willReturn(Optional.empty());

        // when & then -> 연결이 없다고 예외가 나면 나머지 참가자 전송까지 멈춘다
        handle();
        verify(senderConnection).sendProgress(PROGRESS);
        verifyNoInteractions(otherConnection);
    }

    @Test
    @DisplayName("이 인스턴스가 그 방을 안 들고 있으면 아무에게도 보내지 않는다")
    void sendsNothingWhenRoomIsNotHere() {
        // given -> 방 채널은 모든 인스턴스가 받지만 참가자를 든 곳만 실제로 보낸다
        given(loadRunningRoomMembersPort.usersIn(ROOM_ID)).willReturn(Set.of());

        // when
        handle();

        // then
        verify(runningSessionPort, never()).find(any());
        verifyNoInteractions(otherConnection, thirdConnection);
    }
}
