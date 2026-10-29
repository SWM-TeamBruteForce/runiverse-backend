package com.runiverse.running_service.unit_test.match.application;

import com.github.f4b6a3.uuid.UuidCreator;
import com.runiverse.running_service.application.device.command.push.PushRequestedEvent;
import com.runiverse.running_service.application.device.port.out.PushMessage;
import com.runiverse.running_service.application.device.port.out.PushType;
import com.runiverse.running_service.application.match.command.reminder.NotifyRunningReminderCommand;
import com.runiverse.running_service.application.match.command.reminder.NotifyRunningReminderHandler;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@DisplayName("러닝 리마인더 단위 테스트")
class NotifyRunningReminderHandlerTest {

    private static final long ROOM_ID = 125L;
    // 예약이 제때 깼을 때 남는 시간 — 운영 오프셋과 같을 필요는 없다
    private static final Duration LEAD = Duration.ofMinutes(5);
    // now()를 두 번 부르는 사이의 실행 시간만큼 어긋난다 — 그 폭만 허용한다
    private static final long TOLERANCE_MS = 1_000L;

    @Mock
    private LoadRunningRoomPort loadRunningRoomPort;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private NotifyRunningReminderHandler handler;

    private long nextPlayerId = 7L;

    @BeforeEach
    void setUp() {
        handler = new NotifyRunningReminderHandler(loadRunningRoomPort, eventPublisher);
    }

    @Test
    @DisplayName("확정된 매칭 방의 참가자 모두에게 리마인더 푸시를 요청한다")
    void requestsPushToEveryPlayer() {
        // given
        LocalDateTime startAt = startAfter(LEAD);
        UUID first = newUserId();
        UUID second = newUserId();
        givenRoom(room(RunningRoomType.MATCH, RunningRoomStatus.MATCHED, startAt,
                session(first, true), session(second, true)));

        // when
        handler.handle(new NotifyRunningReminderCommand(ROOM_ID));

        // then -> 문구는 발화 시점의 남은 분과 방의 시작 시각으로 만든다
        List<PushRequestedEvent> events = publishedEvents(2);
        assertThat(events).extracting(PushRequestedEvent::userId).containsExactly(first, second);
        PushMessage message = events.getFirst().message();
        assertThat(message.type()).isEqualTo(PushType.RUNNING_REMINDER);
        assertThat(message.title()).isEqualTo("출발 5분 전이에요");
        assertThat(message.body())
                .isEqualTo(startAt.format(DateTimeFormatter.ofPattern("HH:mm")) + "에 출발해요. 로비에서 기다려주세요");
        assertThat(message.data()).isEqualTo(Map.of("runningRoomId", String.valueOf(ROOM_ID)));
    }

    @Test
    @DisplayName("유효 시간은 지금부터 시작 시각까지다")
    void ttlLastsUntilStart() {
        // given
        givenRoom(room(RunningRoomType.MATCH, RunningRoomStatus.MATCHED, startAfter(LEAD),
                session(newUserId(), true)));

        // when
        handler.handle(new NotifyRunningReminderCommand(ROOM_ID));

        // then -> 꺼져 있던 폰이 시작 뒤에 켜지면 받지 않아야 한다
        assertThat(publishedEvents(1).getFirst().message().ttl().toMillis())
                .isBetween(LEAD.toMillis() - TOLERANCE_MS, LEAD.toMillis());
    }

    @Test
    @DisplayName("남은 시간을 분으로 올려 제목에 쓴다")
    void roundsMinutesUp() {
        // given -> 예약이 밀려 4분 30초 남은 시점에 깼다
        givenRoom(room(RunningRoomType.MATCH, RunningRoomStatus.MATCHED,
                startAfter(Duration.ofSeconds(270)), session(newUserId(), true)));

        // when
        handler.handle(new NotifyRunningReminderCommand(ROOM_ID));

        // then -> 내리면 정시 발화도 몇 ms 늦어 "4분 전"이 된다
        assertThat(publishedEvents(1).getFirst().message().title()).isEqualTo("출발 5분 전이에요");
    }

    @Test
    @DisplayName("모집 중인 초대 방에도 보낸다")
    void notifiesRecruitingInviteRoom() {
        // given -> 초대 방은 마감이 시작 1분 전이라 5분 전에는 아직 MATCHING이다
        UUID inviter = newUserId();
        givenRoom(room(RunningRoomType.INVITE, RunningRoomStatus.MATCHING, startAfter(LEAD),
                session(inviter, true)));

        // when
        handler.handle(new NotifyRunningReminderCommand(ROOM_ID));

        // then
        assertThat(publishedEvents(1)).extracting(PushRequestedEvent::userId).containsExactly(inviter);
    }

    @Test
    @DisplayName("방에서 나간 사람에게는 보내지 않는다")
    void skipsLeftPlayer() {
        // given
        UUID stayed = newUserId();
        givenRoom(room(RunningRoomType.MATCH, RunningRoomStatus.MATCHED, startAfter(LEAD),
                session(stayed, true), session(newUserId(), false)));

        // when
        handler.handle(new NotifyRunningReminderCommand(ROOM_ID));

        // then
        assertThat(publishedEvents(1)).extracting(PushRequestedEvent::userId).containsExactly(stayed);
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(value = RunningRoomStatus.class, names = {"STARTED", "FINISHED", "CANCELLED"})
    @DisplayName("시작했거나 닫힌 방에는 보내지 않는다")
    void skipsRoomNotBeforeStart(RunningRoomStatus status) {
        // given -> 전원이 나가 취소됐거나 먼저 도착한 참가자가 방을 올렸다
        givenRoom(room(RunningRoomType.MATCH, status, startAfter(LEAD), session(newUserId(), true)));

        // when
        handler.handle(new NotifyRunningReminderCommand(ROOM_ID));

        // then
        verifyNoInteractions(eventPublisher);
    }

    @Test
    @DisplayName("시작 시각이 지났으면 보내지 않는다")
    void skipsAfterStart() {
        // given -> 서버가 멈췄다 복구되면 지난 예약도 바로 돈다
        givenRoom(room(RunningRoomType.MATCH, RunningRoomStatus.MATCHED,
                LocalDateTime.now().minusSeconds(5), session(newUserId(), true)));

        // when
        handler.handle(new NotifyRunningReminderCommand(ROOM_ID));

        // then
        verifyNoInteractions(eventPublisher);
    }

    @Test
    @DisplayName("방이 없으면 아무것도 하지 않는다")
    void skipsWhenRoomIsGone() {
        // given
        given(loadRunningRoomPort.loadById(new RunningRoomId(ROOM_ID))).willReturn(Optional.empty());

        // when
        handler.handle(new NotifyRunningReminderCommand(ROOM_ID));

        // then
        verifyNoInteractions(eventPublisher);
    }

    private void givenRoom(RunningRoom room) {
        given(loadRunningRoomPort.loadById(new RunningRoomId(ROOM_ID))).willReturn(Optional.of(room));
    }

    private List<PushRequestedEvent> publishedEvents(int count) {
        ArgumentCaptor<PushRequestedEvent> captor = ArgumentCaptor.forClass(PushRequestedEvent.class);
        verify(eventPublisher, times(count)).publishEvent(captor.capture());
        return captor.getAllValues();
    }

    private static UUID newUserId() {
        return UuidCreator.getTimeOrderedEpoch();
    }

    private static LocalDateTime startAfter(Duration untilStart) {
        return LocalDateTime.now().plus(untilStart);
    }

    private SessionDraft session(UUID userId, boolean connected) {
        return new SessionDraft(new UserId(userId), new RunningPlayerId(nextPlayerId++), connected ? 0 : 1, connected);
    }

    private static RunningRoom room(RunningRoomType type, RunningRoomStatus status, LocalDateTime startAt,
                                    SessionDraft... sessions) {
        int connectedCount = (int) List.of(sessions).stream().filter(SessionDraft::connected).count();
        return RunningRoom.builder()
                .runningRoomId(ROOM_ID)
                .type(type)
                .status(status)
                // 종료 상태는 닫힌 시각이 있어야 복원된다
                .closeAt(status.isTerminal() ? LocalDateTime.now() : null)
                .startAt(startAt)
                .targetDistance(5_000)
                .avgPace(360)
                .currentPlayerCount(connectedCount)
                .maxPlayerCount(4)
                .sessions(List.of(sessions))
                .build();
    }
}
