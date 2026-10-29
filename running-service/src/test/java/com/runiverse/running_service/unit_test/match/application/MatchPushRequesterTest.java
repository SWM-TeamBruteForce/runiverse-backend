package com.runiverse.running_service.unit_test.match.application;

import com.github.f4b6a3.uuid.UuidCreator;
import com.runiverse.running_service.application.common.port.out.LoadPlayerProfilesPort;
import com.runiverse.running_service.application.common.port.out.PlayerProfile;
import com.runiverse.running_service.application.device.command.push.PushRequestedEvent;
import com.runiverse.running_service.application.device.port.out.PushMessage;
import com.runiverse.running_service.application.device.port.out.PushType;
import com.runiverse.running_service.application.match.common.MatchPushRequester;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.running.player.vo.RunningPlayerId;
import com.runiverse.running_service.domain.running.room.InvitationDraft;
import com.runiverse.running_service.domain.running.room.RunningRoom;
import com.runiverse.running_service.domain.running.room.SessionDraft;
import com.runiverse.running_service.domain.running.room.vo.RoomInvitationStatus;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@DisplayName("매칭 확정·초대 수락 푸시 요청 단위 테스트")
class MatchPushRequesterTest {

    private static final long ROOM_ID = 125L;
    private static final LocalDateTime START_AT = LocalDateTime.of(2026, 10, 29, 21, 30);

    @Mock
    private LoadPlayerProfilesPort loadPlayerProfilesPort;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private MatchPushRequester requester;

    private long nextPlayerId = 7L;

    @BeforeEach
    void setUp() {
        requester = new MatchPushRequester(loadPlayerProfilesPort, eventPublisher);
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

    @Test
    @DisplayName("초대 수락은 그 사람을 초대했고 아직 방에 있는 사람에게만 보낸다")
    void invitationAcceptedToStayingInviters() {
        // given -> 초대한 사람 둘 중 하나는 나갔고, 다른 사람이 초대한 손님은 따로 있다
        UserId host = newUserId();
        UserId leftInviter = newUserId();
        UserId invitee = newUserId();
        UserId otherInvitee = newUserId();
        RunningRoom room = inviteRoom(
                List.of(session(host, true), session(leftInviter, false), session(invitee, true)),
                List.of(new InvitationDraft(host, invitee, RoomInvitationStatus.ACCEPTED, 1),
                        new InvitationDraft(leftInviter, invitee, RoomInvitationStatus.ACCEPTED, 1),
                        new InvitationDraft(host, otherInvitee, RoomInvitationStatus.PENDING, 1)));
        givenProfile(invitee, "영희");

        // when
        requester.invitationAccepted(room, invitee);

        // then
        PushRequestedEvent event = publishedEvents(1).getFirst();
        assertThat(event.userId()).isEqualTo(host.value());
        assertThat(event.message()).isEqualTo(new PushMessage(PushType.INVITATION_ACCEPTED,
                "초대를 수락했어요", "영희님이 21:30 러닝에 들어왔어요",
                Map.of("actorId", invitee.value().toString(), "runningRoomId", "125"), START_AT));
    }

    @Test
    @DisplayName("나갔다 다시 초대받아 수락하면 지금 방에 있는 초대한 사람 모두가 받는다")
    void invitationAcceptedAgain() {
        // given -> 처음 초대한 사람과 다시 초대한 사람 둘 다 방에 있다
        UserId host = newUserId();
        UserId reInviter = newUserId();
        UserId invitee = newUserId();
        RunningRoom room = inviteRoom(
                List.of(session(host, true), session(reInviter, true), session(invitee, true)),
                List.of(new InvitationDraft(host, invitee, RoomInvitationStatus.ACCEPTED, 1),
                        new InvitationDraft(reInviter, invitee, RoomInvitationStatus.ACCEPTED, 1)));
        givenProfile(invitee, "영희");

        // when
        requester.invitationAccepted(room, invitee);

        // then
        assertThat(publishedEvents(2)).extracting(PushRequestedEvent::userId)
                .containsExactly(host.value(), reInviter.value());
    }

    @Test
    @DisplayName("초대한 사람이 모두 나갔으면 프로필도 읽지 않고 보내지 않는다")
    void invitationAcceptedWithoutStayingInviter() {
        // given
        UserId leftInviter = newUserId();
        UserId invitee = newUserId();
        RunningRoom room = inviteRoom(
                List.of(session(leftInviter, false), session(newUserId(), true), session(invitee, true)),
                List.of(new InvitationDraft(leftInviter, invitee, RoomInvitationStatus.ACCEPTED, 1)));

        // when
        requester.invitationAccepted(room, invitee);

        // then
        verifyNoInteractions(loadPlayerProfilesPort, eventPublisher);
    }

    @Test
    @DisplayName("수락한 사람의 프로필이 없으면 던지지 않고 보내지 않는다")
    void invitationAcceptedWithoutProfile() {
        // given -> 던지면 수락까지 롤백된다
        UserId host = newUserId();
        UserId invitee = newUserId();
        RunningRoom room = inviteRoom(
                List.of(session(host, true), session(invitee, true)),
                List.of(new InvitationDraft(host, invitee, RoomInvitationStatus.ACCEPTED, 1)));
        given(loadPlayerProfilesPort.loadProfiles(any())).willReturn(Map.of());

        // when
        requester.invitationAccepted(room, invitee);

        // then
        verifyNoInteractions(eventPublisher);
    }

    private void givenProfile(UserId userId, String nickname) {
        given(loadPlayerProfilesPort.loadProfiles(List.of(userId.value())))
                .willReturn(Map.of(userId.value(), new PlayerProfile(userId.value(), nickname, null, null)));
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
        return room(RunningRoomType.MATCH, RunningRoomStatus.MATCHED, sessions, List.of());
    }

    private static RunningRoom inviteRoom(List<SessionDraft> sessions, List<InvitationDraft> invitations) {
        return room(RunningRoomType.INVITE, RunningRoomStatus.MATCHING, sessions, invitations);
    }

    private static RunningRoom room(RunningRoomType type, RunningRoomStatus status,
                                    List<SessionDraft> sessions, List<InvitationDraft> invitations) {
        int connectedCount = (int) sessions.stream().filter(SessionDraft::connected).count();
        return RunningRoom.builder()
                .runningRoomId(ROOM_ID)
                .type(type)
                .status(status)
                .startAt(START_AT)
                .targetDistance(5_000)
                .avgPace(360)
                .currentPlayerCount(connectedCount)
                .maxPlayerCount(4)
                .sessions(sessions)
                .invitations(invitations)
                .build();
    }
}
