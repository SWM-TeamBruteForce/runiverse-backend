package com.runiverse.running_service.unit_test.match.application;

import com.github.f4b6a3.uuid.UuidCreator;
import com.runiverse.running_service.application.device.command.push.PushRequestedEvent;
import com.runiverse.running_service.application.device.port.out.PushMessage;
import com.runiverse.running_service.application.device.port.out.PushType;
import com.runiverse.running_service.application.match.common.MatchPushRequester;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.running.player.vo.RunningPlayerId;
import com.runiverse.running_service.domain.running.room.RunningRoom.SessionDraft;
import com.runiverse.running_service.domain.running.room.RunningRoom;
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
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("매칭 확정 푸시 요청 단위 테스트")
class MatchPushRequesterTest {

    private static final long ROOM_ID = 125L;
    private static final LocalDateTime START_AT = LocalDateTime.of(2026, 10, 29, 21, 30);

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private MatchPushRequester requester;

    private long nextPlayerId = 7L;

    @BeforeEach
    void setUp() {
        requester = new MatchPushRequester(eventPublisher);
    }

    @Test
    @DisplayName("매칭 확정은 방에 남은 사람 모두에게 본인을 포함한 인원으로 보낸다")
    void matchConfirmedToEveryStayingPlayer() {
        // given
        UserId first = newUserId();
        UserId second = newUserId();
        UserId third = newUserId();
        RunningRoom room = matchRoom(List.of(session(first, true), session(second, true), session(third, true),
                session(newUserId(), false)));

        // when
        requester.matchConfirmed(room);

        // then -> 나간 사람은 받지도, 인원에 세지도 않는다
        List<PushRequestedEvent> events = publishedEvents(3);
        assertThat(events).extracting(PushRequestedEvent::userId)
                .containsExactly(first.value(), second.value(), third.value());
        assertThat(events.getFirst().message()).isEqualTo(new PushMessage(PushType.MATCH_CONFIRMED,
                "매칭이 완료됐어요", "21:30 러닝에 3명이 모였어요",
                Map.of("runningRoomId", "125"), START_AT));
    }

    @Test
    @DisplayName("혼자 남은 매칭 확정은 혼자 달린다고 알린다")
    void matchConfirmedAlone() {
        // given
        UserId alone = newUserId();
        RunningRoom room = matchRoom(List.of(session(alone, true), session(newUserId(), false)));

        // when
        requester.matchConfirmed(room);

        // then
        PushRequestedEvent event = publishedEvents(1).getFirst();
        assertThat(event.userId()).isEqualTo(alone.value());
        assertThat(event.message()).isEqualTo(new PushMessage(PushType.MATCH_CONFIRMED,
                "혼자 달리게 됐어요", "21:30에 혼자 달려요. 지금 나가도 다시 신청할 수 있어요",
                Map.of("runningRoomId", "125"), START_AT));
    }

    private List<PushRequestedEvent> publishedEvents(int count) {
        ArgumentCaptor<PushRequestedEvent> captor = ArgumentCaptor.forClass(PushRequestedEvent.class);
        verify(eventPublisher, times(count)).publishEvent(captor.capture());
        return captor.getAllValues();
    }

    private static UserId newUserId() {
        return new UserId(UuidCreator.getTimeOrderedEpoch());
    }

    private SessionDraft session(UserId userId, boolean connected) {
        return new SessionDraft(userId, new RunningPlayerId(nextPlayerId++), connected ? 0 : 1, connected);
    }

    private static RunningRoom matchRoom(List<SessionDraft> sessions) {
        int connectedCount = (int) sessions.stream().filter(SessionDraft::connected).count();
        return RunningRoom.builder()
                .runningRoomId(ROOM_ID)
                .type(RunningRoomType.MATCH)
                .status(RunningRoomStatus.MATCHED)
                .startAt(START_AT)
                .targetDistance(5_000)
                .avgPace(360)
                .currentPlayerCount(connectedCount)
                .maxPlayerCount(4)
                .sessions(sessions)
                .invitations(List.of())
                .build();
    }
}
