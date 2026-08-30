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
    @DisplayName("같은 방의 다른 참가자에게 진행 정보를 보낸다")
    void sendsToOtherMembers() {
        // given
        given(loadRunningRoomMembersPort.usersIn(ROOM_ID))
                .willReturn(Set.of(new UserId(SENDER_ID), new UserId(OTHER_ID),
                        new UserId(THIRD_ID)));
        given(runningSessionPort.find(new UserId(OTHER_ID)))
                .willReturn(Optional.of(otherConnection));
        given(runningSessionPort.find(new UserId(THIRD_ID)))
                .willReturn(Optional.of(thirdConnection));

        // when
        handle();

        // then
        verify(otherConnection).sendProgress(PROGRESS);
        verify(thirdConnection).sendProgress(PROGRESS);
    }

    @Test
    @DisplayName("본인에게는 보내지 않는다")
    void doesNotSendToSender() {
        // given -> 본인 진행은 클라가 이미 계산해 화면에 띄우고 있다(api-spec 5-D)
        given(loadRunningRoomMembersPort.usersIn(ROOM_ID))
                .willReturn(Set.of(new UserId(SENDER_ID)));

        // when
        handle();

        // then -> 명부에 본인뿐이면 연결을 찾을 일조차 없다
        verify(runningSessionPort, never()).find(new UserId(SENDER_ID));
        verifyNoInteractions(otherConnection);
    }

    @Test
    @DisplayName("다른 인스턴스에 붙은 참가자는 건너뛴다")
    void skipsMembersConnectedElsewhere() {
        // given -> 명부에는 있지만 이 인스턴스에 소켓이 없다.
        // 그쪽 인스턴스가 같은 메시지를 받아 자기 몫을 보낸다
        given(loadRunningRoomMembersPort.usersIn(ROOM_ID))
                .willReturn(Set.of(new UserId(SENDER_ID), new UserId(OTHER_ID)));
        given(runningSessionPort.find(new UserId(OTHER_ID))).willReturn(Optional.empty());

        // when & then -> 연결이 없다고 예외가 나면 나머지 참가자 전송까지 멈춘다
        handle();
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
