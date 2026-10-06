package com.runiverse.running_service.unit_test.match.application;

import com.github.f4b6a3.uuid.UuidCreator;
import com.runiverse.running_service.application.match.command.close.CloseMatchingCommand;
import com.runiverse.running_service.application.match.command.close.CloseMatchingHandler;
import com.runiverse.running_service.application.match.common.MatchRoomChangedEvent;
import com.runiverse.running_service.application.match.common.RoomInfoAssembler;
import com.runiverse.running_service.application.match.port.out.LockMatchRoomPort;
import com.runiverse.running_service.application.match.port.out.MatchEventType;
import com.runiverse.running_service.application.match.port.out.MatchStreamEvent;
import com.runiverse.running_service.application.match.port.out.RoomInfo;
import com.runiverse.running_service.application.match.port.out.UpdateMatchRoomPort;
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
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@DisplayName("모집 마감 확정 단위 테스트")
class CloseMatchingHandlerTest {

    private static final long ROOM_ID = 125L;
    private static final int AVG_PACE = 360;
    private static final int TARGET_DISTANCE = 5_000;
    // 조립 결과는 이 테스트의 주제가 아니다 — 어떤 이벤트로 나가는지만 본다
    private static final RoomInfo ROOM_INFO = new RoomInfo(
            ROOM_ID, RunningRoomStatus.MATCHED, LocalDateTime.now().plusMinutes(10),
            LocalDateTime.now(), TARGET_DISTANCE, AVG_PACE, List.of());

    @Mock
    private LockMatchRoomPort lockMatchRoomPort;

    @Mock
    private UpdateMatchRoomPort updateMatchRoomPort;

    @Mock
    private RoomInfoAssembler roomInfoAssembler;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private CloseMatchingHandler closeMatchingHandler;

    @BeforeEach
    void setUp() {
        closeMatchingHandler = new CloseMatchingHandler(
                lockMatchRoomPort, updateMatchRoomPort, roomInfoAssembler, eventPublisher);
    }

    @Test
    @DisplayName("모집 중인 방을 확정한다")
    void closesMatchingRoom() {
        // given -> 예약이 마감 시각에 깨워서 부른다
        givenRoom(room(RunningRoomStatus.MATCHING, 3));

        // when
        closeMatchingHandler.handle(new CloseMatchingCommand(ROOM_ID));

        // then
        assertThat(updatedRoom().getStatus()).isEqualTo(RunningRoomStatus.MATCHED);
    }

    @Test
    @DisplayName("확정된 순간에는 MATCH_STARTED로 알린다")
    void publishesStartedOnClose() {
        // given -> 재연결 스냅샷(UPDATED)과 갈라야 확정 연출이 반복되지 않는다(api-spec 5-B)
        givenRoom(room(RunningRoomStatus.MATCHING, 3));
        given(roomInfoAssembler.assemble(any(RunningRoom.class))).willReturn(ROOM_INFO);

        // when
        closeMatchingHandler.handle(new CloseMatchingCommand(ROOM_ID));

        // then
        verify(eventPublisher).publishEvent(new MatchRoomChangedEvent(
                MatchStreamEvent.started(ROOM_INFO)));
    }

    @Test
    @DisplayName("1인 방도 인원과 무관하게 확정한다")
    void closesEvenWithSinglePlayer() {
        // given -> 인원 부족으로 인한 자동 취소는 없다. 혼자 뛴다(feature-spec)
        givenRoom(room(RunningRoomStatus.MATCHING, 1));
        given(roomInfoAssembler.assemble(any(RunningRoom.class))).willReturn(ROOM_INFO);

        // when
        closeMatchingHandler.handle(new CloseMatchingCommand(ROOM_ID));

        // then
        assertThat(updatedRoom().getStatus()).isEqualTo(RunningRoomStatus.MATCHED);
        verify(eventPublisher).publishEvent(any(MatchRoomChangedEvent.class));
    }

    @Test
    @DisplayName("이미 확정된 방은 두 번 확정하지 않는다")
    void skipsAlreadyClosedRoom() {
        // given -> 예약은 인스턴스 여럿이 들고 있고 재시도도 있어 두 번 깰 수 있다
        givenRoom(room(RunningRoomStatus.MATCHED, 3));

        // when
        closeMatchingHandler.handle(new CloseMatchingCommand(ROOM_ID));

        // then -> 상태 재확인이 곧 멱등성이다. 확정 연출이 두 번 뜨지 않는다
        verifyNoInteractions(updateMatchRoomPort, roomInfoAssembler, eventPublisher);
    }

    @Test
    @DisplayName("전원 취소로 닫힌 방은 확정하지 않는다")
    void skipsCancelledRoom() {
        // given -> 예약을 걸어둔 뒤 마지막 참가자가 나가 방이 CANCELLED가 됐다.
        //          CANCELLED는 terminal이라 되살리면 안 된다
        givenRoom(room(RunningRoomStatus.CANCELLED, 1));

        // when
        closeMatchingHandler.handle(new CloseMatchingCommand(ROOM_ID));

        // then
        verifyNoInteractions(updateMatchRoomPort, roomInfoAssembler, eventPublisher);
    }

    @Test
    @DisplayName("방이 없으면 아무것도 하지 않는다")
    void skipsWhenRoomIsGone() {
        // given -> 예약만 남고 방이 사라진 경우. 예약을 소비한 것으로 보고 끝낸다
        given(lockMatchRoomPort.lockById(new RunningRoomId(ROOM_ID))).willReturn(Optional.empty());

        // when
        closeMatchingHandler.handle(new CloseMatchingCommand(ROOM_ID));

        // then
        verifyNoInteractions(updateMatchRoomPort, roomInfoAssembler, eventPublisher);
    }

    private void givenRoom(RunningRoom room) {
        given(lockMatchRoomPort.lockById(new RunningRoomId(ROOM_ID))).willReturn(Optional.of(room));
        // 확정되면 이벤트가 나간다 — 조립 결과는 이 테스트의 주제가 아니라 값만 채워둔다.
        // 확정하지 않고 빠지는 케이스도 있어 lenient다
        lenient().when(roomInfoAssembler.assemble(any(RunningRoom.class))).thenReturn(ROOM_INFO);
    }

    private RunningRoom updatedRoom() {
        ArgumentCaptor<RunningRoom> captor = ArgumentCaptor.forClass(RunningRoom.class);
        verify(updateMatchRoomPort).update(captor.capture());
        return captor.getValue();
    }

    // 마감 시각은 예약이 이미 판단했다 — 핸들러는 시각을 다시 보지 않으므로 start_at은 아무 값이어도 된다
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
                .startAt(LocalDateTime.now().plusMinutes(10))
                .targetDistance(TARGET_DISTANCE)
                .avgPace(AVG_PACE)
                .currentPlayerCount(currentPlayerCount)
                .maxPlayerCount(4)
                .sessions(sessions)
                .closeAt(status == RunningRoomStatus.CANCELLED ? LocalDateTime.now() : null)
                .build();
    }
}
