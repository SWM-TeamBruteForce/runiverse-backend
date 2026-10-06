package com.runiverse.running_service.unit_test.match.application;

import com.github.f4b6a3.uuid.UuidCreator;
import com.runiverse.running_service.application.match.command.ready.NotifyRunningReadyCommand;
import com.runiverse.running_service.application.match.command.ready.NotifyRunningReadyHandler;
import com.runiverse.running_service.application.match.common.MatchRoomChangedEvent;
import com.runiverse.running_service.application.match.port.out.MatchEventType;
import com.runiverse.running_service.application.match.port.out.RunningReady;
import com.runiverse.running_service.application.running.port.out.LoadRunningRoomPort;
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

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@DisplayName("러닝 시작 통지 단위 테스트")
class NotifyRunningReadyHandlerTest {

    private static final long ROOM_ID = 125L;
    private static final int AVG_PACE = 360;
    private static final int TARGET_DISTANCE = 5_000;
    // 예약이 제때 깼을 때 남는 시간 — 운영 리드타임과 같을 필요는 없다
    private static final Duration LEAD = Duration.ofSeconds(10);
    // now()를 두 번 부르는 사이의 실행 시간만큼 어긋난다 — 그 폭만 허용한다
    private static final long TOLERANCE_MS = 1_000L;

    @Mock
    private LoadRunningRoomPort loadRunningRoomPort;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private NotifyRunningReadyHandler notifyRunningReadyHandler;

    @BeforeEach
    void setUp() {
        notifyRunningReadyHandler = new NotifyRunningReadyHandler(
                loadRunningRoomPort, eventPublisher);
    }

    @Test
    @DisplayName("확정된 방에는 시작 통지를 보낸다")
    void notifiesMatchedRoom() {
        // given -> 예약이 start_at - 리드타임에 깨워서 부른다
        givenRoom(room(RunningRoomStatus.MATCHED, startAfter(LEAD), 3));

        // when
        notifyRunningReadyHandler.handle(new NotifyRunningReadyCommand(ROOM_ID));

        // then -> 클라는 이 값으로 발사 타이머를 건다(api-spec 5-C)
        RunningReady ready = publishedReady();
        assertThat(ready.runningRoomId()).isEqualTo(ROOM_ID);
        assertThat(ready.startsInMs())
                .isBetween(LEAD.toMillis() - TOLERANCE_MS, LEAD.toMillis());
    }

    @Test
    @DisplayName("방 상태를 바꾸지 않고 알리기만 한다")
    void doesNotChangeRoomStatus() {
        // given -> MATCHED→STARTED는 첫 참가자의 RUNNING_START 몫이다(feature-spec)
        RunningRoom room = room(RunningRoomStatus.MATCHED, startAfter(LEAD), 3);
        givenRoom(room);

        // when
        notifyRunningReadyHandler.handle(new NotifyRunningReadyCommand(ROOM_ID));

        // then
        assertThat(room.getStatus()).isEqualTo(RunningRoomStatus.MATCHED);
    }

    @Test
    @DisplayName("발화가 밀려 시작 시각을 넘겼으면 남은 시간은 0이다")
    void clampsToZeroWhenLate() {
        // given -> 스케줄러가 밀렸다. 음수를 내려보내면 클라 타이머 동작이 제각각이라 0으로 누른다
        givenRoom(room(RunningRoomStatus.MATCHED,
                LocalDateTime.now().minusSeconds(5), 3));

        // when
        notifyRunningReadyHandler.handle(new NotifyRunningReadyCommand(ROOM_ID));

        // then -> 클라는 즉시 RUNNING_START를 쏜다
        assertThat(publishedReady().startsInMs()).isZero();
    }

    @Test
    @DisplayName("예약 시각이 아니라 발화 시점을 기준으로 남은 시간을 잰다")
    void measuresFromNowNotFromSchedule() {
        // given -> 예약은 10초 전이었지만 실제로는 3초 남은 시점에 깼다
        givenRoom(room(RunningRoomStatus.MATCHED,
                startAfter(Duration.ofSeconds(3)), 3));

        // when
        notifyRunningReadyHandler.handle(new NotifyRunningReadyCommand(ROOM_ID));

        // then -> 리드타임을 그대로 실으면 클라가 7초 늦게 쏜다
        assertThat(publishedReady().startsInMs())
                .isBetween(Duration.ofSeconds(3).toMillis() - TOLERANCE_MS,
                        Duration.ofSeconds(3).toMillis());
    }

    @Test
    @DisplayName("전원 취소로 닫힌 방에는 알리지 않는다")
    void skipsCancelledRoom() {
        // given -> 알리면 클라가 홈으로 갔다가 러닝 화면으로 튄다
        givenRoom(room(RunningRoomStatus.CANCELLED, startAfter(LEAD), 1));

        // when
        notifyRunningReadyHandler.handle(new NotifyRunningReadyCommand(ROOM_ID));

        // then
        verifyNoInteractions(eventPublisher);
    }

    @Test
    @DisplayName("이미 시작된 방에는 알리지 않는다")
    void skipsStartedRoom() {
        // given -> 다른 참가자의 RUNNING_START가 먼저 도착해 방을 올렸다
        givenRoom(room(RunningRoomStatus.STARTED, startAfter(LEAD), 3));

        // when
        notifyRunningReadyHandler.handle(new NotifyRunningReadyCommand(ROOM_ID));

        // then
        verifyNoInteractions(eventPublisher);
    }

    @Test
    @DisplayName("아직 모집 중인 방에는 알리지 않는다")
    void skipsMatchingRoom() {
        // given -> 마감 예약이 밀려 아직 MATCHING이다. 확정도 하지 않고 알리지도 않는다
        givenRoom(room(RunningRoomStatus.MATCHING, startAfter(LEAD), 2));

        // when
        notifyRunningReadyHandler.handle(new NotifyRunningReadyCommand(ROOM_ID));

        // then
        verifyNoInteractions(eventPublisher);
    }

    @Test
    @DisplayName("방이 없으면 아무것도 하지 않는다")
    void skipsWhenRoomIsGone() {
        // given -> 예약만 남고 방이 사라진 경우. 마감 핸들러와 같은 규칙이다
        given(loadRunningRoomPort.loadById(new RunningRoomId(ROOM_ID)))
                .willReturn(Optional.empty());

        // when
        notifyRunningReadyHandler.handle(new NotifyRunningReadyCommand(ROOM_ID));

        // then
        verifyNoInteractions(eventPublisher);
    }

    private void givenRoom(RunningRoom room) {
        given(loadRunningRoomPort.loadById(new RunningRoomId(ROOM_ID)))
                .willReturn(Optional.of(room));
    }

    // 이벤트 이름까지 함께 본다 — 클라가 이름으로 분기하므로 타입이 곧 계약이다
    private RunningReady publishedReady() {
        ArgumentCaptor<MatchRoomChangedEvent> captor =
                ArgumentCaptor.forClass(MatchRoomChangedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().event().type()).isEqualTo(MatchEventType.RUNNING_READY);
        return captor.getValue().event().ready();
    }

    private static LocalDateTime startAfter(Duration untilStart) {
        return LocalDateTime.now().plus(untilStart);
    }

    private static RunningRoom room(RunningRoomStatus status, LocalDateTime startAt,
                                    int currentPlayerCount) {
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
                // 종료 상태는 닫힌 시각이 있어야 복원된다
                .closeAt(status.isTerminal() ? LocalDateTime.now() : null)
                .startAt(startAt)
                .targetDistance(TARGET_DISTANCE)
                .avgPace(AVG_PACE)
                .currentPlayerCount(currentPlayerCount)
                .maxPlayerCount(4)
                .sessions(sessions)
                .build();
    }
}
