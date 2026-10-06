package com.runiverse.running_service.unit_test.running.application;

import com.github.f4b6a3.uuid.UuidCreator;
import com.runiverse.running_service.application.running.command.start.StartRunningRoomCommand;
import com.runiverse.running_service.application.running.command.start.StartRunningRoomHandler;
import com.runiverse.running_service.application.running.port.out.LockRunningRoomPort;
import com.runiverse.running_service.application.running.port.out.UpdateRunningRoomPort;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.running.player.vo.RunningPlayerId;
import com.runiverse.running_service.domain.running.room.RunningRoom;
import com.runiverse.running_service.domain.running.room.SessionDraft;
import com.runiverse.running_service.domain.running.room.vo.RunningRoomId;
import com.runiverse.running_service.domain.running.room.vo.RunningRoomStatus;
import com.runiverse.running_service.domain.running.room.vo.RunningRoomType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@DisplayName("시작 시각 정각 방 전이 단위 테스트")
class StartRunningRoomHandlerTest {

    private static final long ROOM_ID = 125L;
    private static final int AVG_PACE = 360;
    private static final int TARGET_DISTANCE = 5_000;

    @Mock
    private LockRunningRoomPort lockRunningRoomPort;

    @Mock
    private UpdateRunningRoomPort updateRunningRoomPort;

    private StartRunningRoomHandler startRunningRoomHandler;

    @BeforeEach
    void setUp() {
        startRunningRoomHandler = new StartRunningRoomHandler(
                lockRunningRoomPort, updateRunningRoomPort);
    }

    @Test
    @DisplayName("확정된 방을 정각에 시작한다")
    void startsMatchedRoom() {
        // given -> 예약이 시작 시각에 깨워서 부른다
        givenRoom(room(RunningRoomStatus.MATCHED, 3));

        // when
        startRunningRoomHandler.handle(new StartRunningRoomCommand(ROOM_ID));

        // then
        assertThat(updatedRoom().getStatus()).isEqualTo(RunningRoomStatus.STARTED);
    }

    @Test
    @DisplayName("아무도 채널에 붙지 않은 방도 시작한다")
    void startsRoomNobodyConnectedTo() {
        // given -> 참가자가 한 명도 시작 메시지를 보내지 않았다.
        //          여기서 올려두지 않으면 방이 확정 상태에 갇혀 뒤이은 종료가 닫을 수 없다
        givenRoom(room(RunningRoomStatus.MATCHED, 1));

        // when
        startRunningRoomHandler.handle(new StartRunningRoomCommand(ROOM_ID));

        // then
        assertThat(updatedRoom().getStatus()).isEqualTo(RunningRoomStatus.STARTED);
    }

    @Test
    @DisplayName("먼저 붙은 참가자가 이미 올린 방은 두 번 올리지 않는다")
    void skipsAlreadyStartedRoom() {
        // given -> 정각 직전에 도착한 참가자의 시작 메시지가 이겼다.
        //          예약은 인스턴스 여럿이 들고 있고 재시도도 있어 두 번 깰 수 있다
        givenRoom(room(RunningRoomStatus.STARTED, 3));

        // when
        startRunningRoomHandler.handle(new StartRunningRoomCommand(ROOM_ID));

        // then -> 상태 재확인이 곧 멱등성이다
        verifyNoInteractions(updateRunningRoomPort);
    }

    @Test
    @DisplayName("전원 이탈로 닫힌 방은 시작하지 않는다")
    void skipsCancelledRoom() {
        // given -> 예약을 걸어둔 뒤 마지막 참가자가 나가 방이 CANCELLED가 됐다.
        //          CANCELLED는 terminal이라 되살리면 안 된다
        givenRoom(room(RunningRoomStatus.CANCELLED, 0));

        // when
        startRunningRoomHandler.handle(new StartRunningRoomCommand(ROOM_ID));

        // then
        verifyNoInteractions(updateRunningRoomPort);
    }

    @Test
    @DisplayName("아직 모집 중인 방은 시작하지 않는다")
    void skipsStillMatchingRoom() {
        // given -> 모집 마감 예약이 실패해야만 나오는 상태다.
        //          MATCHING에서 start()를 부르면 전이 규칙 위반으로 던지므로 그 앞에서 걸러야 한다
        givenRoom(room(RunningRoomStatus.MATCHING, 2));

        // when -> 예외 없이 빠진다. 던지면 예약이 롤백돼 다른 인스턴스가 계속 재시도한다
        startRunningRoomHandler.handle(new StartRunningRoomCommand(ROOM_ID));

        // then -> 마감을 여기서 대신 해주지 않는다. 확정은 마감 예약의 몫이다
        verifyNoInteractions(updateRunningRoomPort);
    }

    @Test
    @DisplayName("방이 없으면 아무것도 하지 않는다")
    void skipsWhenRoomIsGone() {
        // given -> 예약만 남고 방이 사라진 경우. 예약을 소비한 것으로 보고 끝낸다
        given(lockRunningRoomPort.lockById(new RunningRoomId(ROOM_ID)))
                .willReturn(Optional.empty());

        // when
        startRunningRoomHandler.handle(new StartRunningRoomCommand(ROOM_ID));

        // then
        verifyNoInteractions(updateRunningRoomPort);
    }

    private void givenRoom(RunningRoom room) {
        given(lockRunningRoomPort.lockById(new RunningRoomId(ROOM_ID)))
                .willReturn(Optional.of(room));
    }

    private RunningRoom updatedRoom() {
        ArgumentCaptor<RunningRoom> captor = ArgumentCaptor.forClass(RunningRoom.class);
        verify(updateRunningRoomPort).update(captor.capture());
        return captor.getValue();
    }

    // 시작 시각은 예약이 이미 판단했다 — 핸들러는 시각을 다시 보지 않으므로 start_at은 아무 값이어도 된다
    private static RunningRoom room(RunningRoomStatus status, int currentPlayerCount) {
        List<SessionDraft> sessions = new ArrayList<>();
        for (int i = 0; i < currentPlayerCount; i++) {
            sessions.add(new SessionDraft(
                    new UserId(UuidCreator.getTimeOrderedEpoch()),
                    new RunningPlayerId(7L + i), 0, true));
        }
        return RunningRoom.builder()
                .runningRoomId(ROOM_ID)
                .type(RunningRoomType.MATCH)
                .status(status)
                .startAt(LocalDateTime.now())
                .targetDistance(TARGET_DISTANCE)
                .avgPace(AVG_PACE)
                .currentPlayerCount(currentPlayerCount)
                .maxPlayerCount(4)
                .sessions(sessions)
                .closeAt(status == RunningRoomStatus.CANCELLED ? LocalDateTime.now() : null)
                .build();
    }
}
