package com.runiverse.running_service.integration_test.match;

import com.runiverse.running_service.application.auth.command.signup.SignUpCommand;
import com.runiverse.running_service.application.auth.command.signup.SignUpHandler;
import com.runiverse.running_service.application.match.command.broadcast.BroadMatchEventHandler;
import com.runiverse.running_service.application.match.command.broadcast.BroadcastMatchEventCommand;
import com.runiverse.running_service.application.match.command.cancel.CancelMatchCommand;
import com.runiverse.running_service.application.match.command.cancel.CancelMatchHandler;
import com.runiverse.running_service.application.match.command.close.CloseMatchingCommand;
import com.runiverse.running_service.application.match.command.close.CloseMatchingHandler;
import com.runiverse.running_service.application.match.command.inviteaccept.AcceptInvitationCommand;
import com.runiverse.running_service.application.match.command.inviteaccept.AcceptInvitationHandler;
import com.runiverse.running_service.application.match.command.invitedecline.DeclineInvitationCommand;
import com.runiverse.running_service.application.match.command.invitedecline.DeclineInvitationHandler;
import com.runiverse.running_service.application.match.command.inviteopen.OpenInviteRoomCommand;
import com.runiverse.running_service.application.match.command.inviteopen.OpenInviteRoomHandler;
import com.runiverse.running_service.application.match.command.invitesend.SendInvitationCommand;
import com.runiverse.running_service.application.match.command.invitesend.SendInvitationHandler;
import com.runiverse.running_service.application.match.common.InviteProperties;
import com.runiverse.running_service.application.match.common.InviteStartPolicy;
import com.runiverse.running_service.application.match.common.MatchProperties;
import com.runiverse.running_service.application.match.common.MatchRoomChangedEvent;
import com.runiverse.running_service.application.match.common.RecruitmentClosePolicy;
import com.runiverse.running_service.application.match.common.RoomInfoAssembler;
import com.runiverse.running_service.application.match.exception.InvitationNotFoundException;
import com.runiverse.running_service.application.match.exception.InviteCountExceededException;
import com.runiverse.running_service.application.match.exception.InviteeAlreadyInRoomException;
import com.runiverse.running_service.application.match.exception.InviteeInMatchingException;
import com.runiverse.running_service.application.match.exception.NotFriendException;
import com.runiverse.running_service.application.match.exception.RunningInvitationClosedException;
import com.runiverse.running_service.application.match.exception.RunningInvitationNotPendingException;
import com.runiverse.running_service.application.match.exception.RunningRoomFullException;
import com.runiverse.running_service.application.match.port.out.MatchEventType;
import com.runiverse.running_service.application.match.port.out.MatchStreamEvent;
import com.runiverse.running_service.application.match.port.out.RoomInfo;
import com.runiverse.running_service.application.running.exception.AlreadyRunningException;
import com.runiverse.running_service.application.scheduling.command.schedule.ScheduleJobHandler;
import com.runiverse.running_service.application.user.command.onboarding.CompleteOnboardingCommand;
import com.runiverse.running_service.application.user.command.onboarding.CompleteOnboardingHandler;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.running.player.RunningPlayer;
import com.runiverse.running_service.domain.running.player.vo.RunningPlayerStatus;
import com.runiverse.running_service.domain.running.room.RoomInvitation;
import com.runiverse.running_service.domain.running.room.RunningRoom;
import com.runiverse.running_service.domain.running.room.vo.RoomInvitationStatus;
import com.runiverse.running_service.domain.running.room.vo.RunningRoomStatus;
import com.runiverse.running_service.infrastructure.sse.MatchRoomMemberRegistry;
import com.runiverse.running_service.infrastructure.sse.MatchStreamRegistryAdapter;
import com.runiverse.running_service.integration_test.IntegrationTestSupport;
import com.runiverse.running_service.integration_test.fake.FakeViewUrlGenerator;
import com.runiverse.running_service.integration_test.fake.InMemoryMatchCooldownStore;
import com.runiverse.running_service.integration_test.fake.InMemoryMatchStore;
import com.runiverse.running_service.integration_test.fake.InMemoryPlayerProfileStore;
import com.runiverse.running_service.integration_test.fake.InMemoryScheduledJobStore;
import com.runiverse.running_service.integration_test.fake.InMemoryUserRelationshipStore;
import com.runiverse.running_service.integration_test.fake.RecordingMatchStreamConnection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

// 개설 → 초대 → 수락·거절 → 나가기 → 다시 초대를 페이크 저장소로 이어 본다.
// 방 갱신이 스트림까지 가는지는 BroadMatchEventHandler를 거쳐 기록 연결로 확인한다
@DisplayName("러닝 초대 통합 테스트")
public class RunningInvitationIntegrationTest extends IntegrationTestSupport {

    private static final String PASSWORD = "Password123!";
    private static final int AVG_PACE = 330;
    private static final int TARGET_DISTANCE = 7_000;
    private static final Duration CLOSE_OFFSET = Duration.ofMinutes(1);
    private static final MatchProperties MATCH_PROPERTIES = new MatchProperties(
            Duration.ofMinutes(10), Duration.ofSeconds(10), Duration.ofHours(6), 10, Duration.ofMinutes(20),
            LocalTime.of(18, 0), LocalTime.of(22, 0), Duration.ofMinutes(30), List.of(3_000, 5_000, 10_000));
    private static final InviteProperties INVITE_PROPERTIES = new InviteProperties(
            Duration.ofMinutes(10), CLOSE_OFFSET, 1_000, 42_195, 5);

    private SignUpHandler signUpHandler;
    private CompleteOnboardingHandler completeOnboardingHandler;
    private InMemoryUserRelationshipStore relationshipStore;
    private MatchRoomMemberRegistry matchRoomMemberRegistry;
    private MatchStreamRegistryAdapter matchStreamRegistry;
    private OpenInviteRoomHandler openHandler;
    private SendInvitationHandler inviteHandler;
    private AcceptInvitationHandler acceptHandler;
    private DeclineInvitationHandler declineHandler;
    private CancelMatchHandler cancelHandler;
    private CloseMatchingHandler closeHandler;
    private LocalDateTime startAt;
    private int userSequence;

    @BeforeEach
    void setUp() {
        signUpHandler = newSignUpHandler();
        completeOnboardingHandler = new CompleteOnboardingHandler(
                userStore, onboardingStore, onboardingStore, onboardingStore);
        relationshipStore = new InMemoryUserRelationshipStore();
        matchRoomMemberRegistry = new MatchRoomMemberRegistry();
        matchStreamRegistry = new MatchStreamRegistryAdapter();
        // 초대·수락은 지금 시각 기준 마감을 본다 — 마감이 한참 남은 방으로 연다
        startAt = LocalDateTime.now().plusHours(1).withSecond(0).withNano(0);

        InMemoryMatchStore matchStore = new InMemoryMatchStore(runningStore);
        BroadMatchEventHandler broadMatchEventHandler =
                new BroadMatchEventHandler(matchRoomMemberRegistry, matchStreamRegistry);
        // MatchEventDispatcher(AFTER_COMMIT) + Redis 왕복 + MatchEventListener를 대신한다
        ApplicationEventPublisher eventPublisher = event -> {
            if (event instanceof MatchRoomChangedEvent changed) {
                broadMatchEventHandler.handle(new BroadcastMatchEventCommand(changed.event()));
            }
        };
        // 개설의 시각 범위만 고정한다 — 핸들러가 지금 시각을 읽어 23:49 이후에 돌면 고를 시각이 없다
        InviteStartPolicy inviteStartPolicy = spy(new InviteStartPolicy(INVITE_PROPERTIES, MATCH_PROPERTIES));
        doReturn(true).when(inviteStartPolicy).isSelectable(any(), any());
        RoomInfoAssembler roomInfoAssembler = new RoomInfoAssembler(
                matchStore,                                                 // LoadMatchPlayersPort
                new InMemoryPlayerProfileStore(userStore, onboardingStore), // LoadPlayerProfilesPort
                new FakeViewUrlGenerator(),                                 // GenerateViewUrlPort
                new RecruitmentClosePolicy(MATCH_PROPERTIES, inviteStartPolicy),
                runningStore,                                               // LoadActiveApplicationPort
                matchStore,                                                 // LoadMatchRoomPort
                runningStore                                                // LoadMatchRoomDetailPort
        );
        openHandler = new OpenInviteRoomHandler(
                inviteStartPolicy, runningStore, onboardingStore, runningStore, runningStore,
                new ScheduleJobHandler(new InMemoryScheduledJobStore(), event -> {
                }), MATCH_PROPERTIES);
        inviteHandler = new SendInvitationHandler(
                runningStore,        // LockMatchRoomPort
                runningStore,        // UpdateMatchRoomPort
                relationshipStore,   // CheckFriendPort
                runningStore,        // LoadUserStatusPort — 친구의 지금 상태
                inviteStartPolicy, INVITE_PROPERTIES, roomInfoAssembler,
                (userId, payload) -> { },   // SendUserNotificationPort — 알림 저장은 이 테스트의 관심사가 아니다
                eventPublisher);
        acceptHandler = new AcceptInvitationHandler(
                runningStore,        // LockMatchRoomPort
                runningStore,        // UpdateMatchRoomPort
                inviteStartPolicy,
                runningStore,        // ExistsActiveApplicationPort
                onboardingStore,     // LoadUserAvgPacePort
                runningStore,        // CreateMatchApplicationPort
                matchStore,          // LoadMatchPlayersPort
                roomInfoAssembler, eventPublisher);
        declineHandler = new DeclineInvitationHandler(
                runningStore, runningStore, roomInfoAssembler, eventPublisher);
        // 나가기는 매칭 방과 같은 POST /match-rooms/leave다
        cancelHandler = new CancelMatchHandler(
                runningStore, matchStore, runningStore, matchStore, runningStore, runningStore,
                new InMemoryMatchCooldownStore(), MATCH_PROPERTIES, roomInfoAssembler, eventPublisher);
        // 모집 마감 예약이 깨워 부르는 핸들러 — 테스트는 시각을 기다리지 않고 바로 부른다
        closeHandler = new CloseMatchingHandler(runningStore, runningStore, roomInfoAssembler, eventPublisher);
    }

    @Test
    @DisplayName("초대하면 방 참가자 스트림에 대기 중인 초대 현황이 INVITE_ROOM_UPDATED로 간다")
    void inviteShowsPendingOnRoomStream() {
        // given
        UUID host = onboardedUser();
        UUID friend = friendOf(host);
        Long roomId = open(host);
        RecordingMatchStreamConnection hostStream = connect(host, roomId);

        // when
        invite(host, roomId, friend);

        // then
        assertThat(inviteCountOf(roomId, host, friend)).isEqualTo(1);
        RoomInfo shown = lastInviteRoomOf(hostStream);
        assertThat(shown.invitations()).singleElement().satisfies(invitee -> {
            assertThat(invitee.userId()).isEqualTo(friend);
            assertThat(invitee.status()).isEqualTo(RoomInvitationStatus.PENDING);
        });
        // 초대는 신청이 아니다 — 받는 사람의 running_players 행이 없다
        assertThat(runningStore.existsActive(new UserId(friend))).isFalse();
    }

    @Test
    @DisplayName("수락하면 신청·세션이 생기고 현황에서 참가자로 옮겨 가며 받은 행이 모두 수락이 된다")
    void acceptMovesInviteeIntoPlayers() {
        // given -> 두 참가자가 같은 친구를 불렀다
        UUID host = onboardedUser();
        UUID member = friendOf(host);
        UUID friend = friendOf(host);
        relationshipStore.seedFriend(member, friend);
        Long roomId = open(host);
        invite(host, roomId, member);
        accept(member, roomId);
        invite(host, roomId, friend);
        invite(member, roomId, friend);
        RecordingMatchStreamConnection hostStream = connect(host, roomId);

        // when
        acceptHandler.handle(new AcceptInvitationCommand(friend, roomId));

        // then
        RunningRoom room = runningStore.findRoom(roomId).orElseThrow();
        assertThat(room.getPlayerCount().current()).isEqualTo(3);
        assertThat(statusesOf(room, friend)).hasSize(2).containsOnly(RoomInvitationStatus.ACCEPTED);
        RunningPlayer player = runningStore.loadActive(new UserId(friend)).orElseThrow();
        assertThat(player.getStatus()).isEqualTo(RunningPlayerStatus.JOINED);
        assertThat(player.getStartAt()).isEqualTo(startAt);
        RoomInfo shown = lastInviteRoomOf(hostStream);
        assertThat(shown.players()).extracting(RoomInfo.RoomPlayer::userId).contains(friend);
        assertThat(shown.invitations()).isEmpty();
    }

    @Test
    @DisplayName("거절하면 현황에서 빠지지만 행은 남아, 다시 초대하면 대기로 돌아오고 횟수는 이어서 센다")
    void declineThenReinvite() {
        // given
        UUID host = onboardedUser();
        UUID friend = friendOf(host);
        Long roomId = open(host);
        invite(host, roomId, friend);
        RecordingMatchStreamConnection hostStream = connect(host, roomId);

        // when
        declineHandler.handle(new DeclineInvitationCommand(friend, roomId));

        // then -> 현황은 응답 전인 초대만 담는다. 행은 지우지 않는다
        assertThat(lastInviteRoomOf(hostStream).invitations()).isEmpty();
        assertThat(runningStore.findRoom(roomId).orElseThrow().invitationStatusOf(new UserId(friend)))
                .contains(RoomInvitationStatus.DECLINED);

        // when -> 같은 사람이 다시 부른다
        invite(host, roomId, friend);

        // then
        assertThat(inviteCountOf(roomId, host, friend)).isEqualTo(2);
        assertThat(lastInviteRoomOf(hostStream).invitations())
                .extracting(RoomInfo.RoomInvitee::status)
                .containsExactly(RoomInvitationStatus.PENDING);
    }

    @Test
    @DisplayName("거절한 초대는 다시 초대받기 전에는 수락할 수 없다")
    void declinedCannotBeAccepted() {
        // given
        UUID host = onboardedUser();
        UUID friend = friendOf(host);
        Long roomId = open(host);
        invite(host, roomId, friend);
        declineHandler.handle(new DeclineInvitationCommand(friend, roomId));

        // when & then
        assertThatThrownBy(() -> accept(friend, roomId)).isInstanceOf(RunningInvitationNotPendingException.class);
        assertThat(runningStore.findRoom(roomId).orElseThrow().getPlayerCount().current()).isEqualTo(1);
    }

    @Test
    @DisplayName("수락해 들어온 사람이 나가면 참가자와 현황 모두에서 빠지고 다시 초대할 수 있다")
    void leaveThenReinvite() {
        // given
        UUID host = onboardedUser();
        UUID friend = friendOf(host);
        Long roomId = open(host);
        invite(host, roomId, friend);
        accept(friend, roomId);
        RecordingMatchStreamConnection hostStream = connect(host, roomId);

        // when -> 나가기는 매칭 방과 같은 경로다
        cancelHandler.handle(new CancelMatchCommand(friend));

        // then -> 초대 방은 언제 나가도 제재가 없다
        RunningRoom room = runningStore.findRoom(roomId).orElseThrow();
        assertThat(statusesOf(room, friend)).containsOnly(RoomInvitationStatus.LEFT);
        assertThat(room.getPlayerCount().current()).isEqualTo(1);
        RoomInfo shown = lastInviteRoomOf(hostStream);
        assertThat(shown.players()).extracting(RoomInfo.RoomPlayer::userId).containsExactly(host);
        assertThat(shown.invitations()).isEmpty();

        // when
        invite(host, roomId, friend);

        // then -> 나간 뒤 다시 들어올 수 있다 — 다시 부르면 대기로 현황에 돌아온다
        assertThat(inviteCountOf(roomId, host, friend)).isEqualTo(2);
        assertThat(lastInviteRoomOf(hostStream).invitations()).extracting(RoomInfo.RoomInvitee::userId)
                .containsExactly(friend);
        accept(friend, roomId);
        assertThat(runningStore.findRoom(roomId).orElseThrow().getPlayerCount().current()).isEqualTo(2);
    }

    @Test
    @DisplayName("초대받지 않은 사람은 방 ID를 알아도 들어갈 수 없다")
    void strangerCannotAcceptByRoomId() {
        // given
        UUID host = onboardedUser();
        UUID stranger = onboardedUser();
        Long roomId = open(host);

        // when & then
        assertThatThrownBy(() -> accept(stranger, roomId)).isInstanceOf(InvitationNotFoundException.class);
        RunningRoom room = runningStore.findRoom(roomId).orElseThrow();
        assertThat(room.getPlayerCount().current()).isEqualTo(1);
        assertThat(room.getSessions()).noneMatch(session -> session.isSameUser(new UserId(stranger)));
        assertThat(runningStore.existsActive(new UserId(stranger))).isFalse();
    }

    @Test
    @DisplayName("친구가 아니면 409이고 초대가 생기지 않는다")
    void nonFriendIsRejected() {
        // given
        UUID host = onboardedUser();
        UUID stranger = onboardedUser();
        Long roomId = open(host);

        // when & then
        assertThatThrownBy(() -> invite(host, roomId, stranger)).isInstanceOf(NotFriendException.class);
        assertThat(runningStore.findRoom(roomId).orElseThrow().getInvitations()).isEmpty();
    }

    @Test
    @DisplayName("다른 방에서 매칭 중인 친구는 매칭 중이라는 이유로 거절되고 초대가 생기지 않는다")
    void rejectsFriendInMatching() {
        // given -> 친구가 자기 초대 방을 열어 활성 신청이 있다
        UUID host = onboardedUser();
        UUID busy = friendOf(host);
        Long roomId = open(host);
        open(busy);

        // when & then -> 건너뛰지 않고 이유를 알린다
        assertThatThrownBy(() -> invite(host, roomId, busy)).isInstanceOf(InviteeInMatchingException.class);
        assertThat(runningStore.findRoom(roomId).orElseThrow().invitationStatusOf(new UserId(busy))).isEmpty();
    }

    @Test
    @DisplayName("이미 방에 들어온 친구를 다시 부르면 이미 방에 있다는 이유로 거절된다")
    void rejectsFriendAlreadyInRoom() {
        // given
        UUID host = onboardedUser();
        UUID friend = friendOf(host);
        Long roomId = open(host);
        invite(host, roomId, friend);
        accept(friend, roomId);

        // when & then
        assertThatThrownBy(() -> invite(host, roomId, friend)).isInstanceOf(InviteeAlreadyInRoomException.class);
    }

    @Test
    @DisplayName("같은 친구에게 상한만큼 보내면 그다음은 횟수 초과로 거절되고 상태는 그대로다")
    void rejectsAfterMaxCount() {
        // given -> 상한은 5회다
        UUID host = onboardedUser();
        UUID friend = friendOf(host);
        Long roomId = open(host);
        for (int i = 0; i < 5; i++) {
            invite(host, roomId, friend);
        }

        // when & then
        assertThatThrownBy(() -> invite(host, roomId, friend)).isInstanceOf(InviteCountExceededException.class);
        assertThat(inviteCountOf(roomId, host, friend)).isEqualTo(5);
    }

    @Test
    @DisplayName("초대받은 뒤 다른 신청을 하면 수락이 막히고, 그 신청을 끝내면 다시 수락할 수 있다")
    void acceptBlockedByLaterApplication() {
        // given
        UUID host = onboardedUser();
        UUID friend = friendOf(host);
        Long roomId = open(host);
        invite(host, roomId, friend);
        open(friend);   // 초대는 신청이 아니라 받은 채로 다른 방을 열 수 있다

        // when & then
        assertThatThrownBy(() -> accept(friend, roomId)).isInstanceOf(AlreadyRunningException.class);
        assertThat(runningStore.findRoom(roomId).orElseThrow().invitationStatusOf(new UserId(friend)))
                .contains(RoomInvitationStatus.PENDING);

        // when -> 자기 방을 나가 활성 신청이 사라졌다
        cancelHandler.handle(new CancelMatchCommand(friend));
        accept(friend, roomId);

        // then
        assertThat(runningStore.findRoom(roomId).orElseThrow().invitationStatusOf(new UserId(friend)))
                .contains(RoomInvitationStatus.ACCEPTED);
    }

    @Test
    @DisplayName("자리가 다 차면 수락이 막히고 초대는 대기로 남아 누가 나가면 수락할 수 있다")
    void fullRoomKeepsInvitationPending() {
        // given -> 4명이 찬 방에도 초대는 보낼 수 있다
        UUID host = onboardedUser();
        Long roomId = open(host);
        List<UUID> members = List.of(friendOf(host), friendOf(host), friendOf(host));
        members.forEach(member -> {
            invite(host, roomId, member);
            accept(member, roomId);
        });
        UUID waiting = friendOf(host);
        invite(host, roomId, waiting);

        // when & then
        assertThatThrownBy(() -> accept(waiting, roomId)).isInstanceOf(RunningRoomFullException.class);
        assertThat(runningStore.findRoom(roomId).orElseThrow().invitationStatusOf(new UserId(waiting)))
                .contains(RoomInvitationStatus.PENDING);

        // when -> 한 명이 나갔다
        cancelHandler.handle(new CancelMatchCommand(members.getFirst()));
        accept(waiting, roomId);

        // then
        assertThat(runningStore.findRoom(roomId).orElseThrow().getPlayerCount().current()).isEqualTo(4);
    }

    @Test
    @DisplayName("모집이 마감되면 대기 중인 초대는 만료로 저장되고 현황이 비며, 그 뒤 수락·거절은 막힌다")
    void closeExpiresPendingInvitations() {
        // given -> 한 명은 대기 중, 한 명은 거절했다
        UUID host = onboardedUser();
        UUID pending = friendOf(host);
        UUID declined = friendOf(host);
        Long roomId = open(host);
        invite(host, roomId, pending, declined);
        declineHandler.handle(new DeclineInvitationCommand(declined, roomId));
        RecordingMatchStreamConnection hostStream = connect(host, roomId);

        // when -> 마감 예약이 깨웠다
        closeHandler.handle(new CloseMatchingCommand(roomId));

        // then -> 저장된 행: 대기만 만료, 거절은 기록으로 남는다
        RunningRoom room = runningStore.findRoom(roomId).orElseThrow();
        assertThat(room.getStatus()).isEqualTo(RunningRoomStatus.MATCHED);
        assertThat(room.invitationStatusOf(new UserId(pending))).contains(RoomInvitationStatus.EXPIRED);
        assertThat(room.invitationStatusOf(new UserId(declined))).contains(RoomInvitationStatus.DECLINED);
        // 마감은 확정 연출 없이 빈 현황으로만 알린다
        RoomInfo shown = lastInviteRoomOf(hostStream);
        assertThat(shown.status()).isEqualTo(RunningRoomStatus.MATCHED);
        assertThat(shown.invitations()).isEmpty();
        // 만료는 방이 닫힌 것이라 수락은 CLOSED, 거절은 응답할 대기 초대가 없어 NOT_PENDING이다
        assertThatThrownBy(() -> accept(pending, roomId)).isInstanceOf(RunningInvitationClosedException.class);
        assertThatThrownBy(() -> declineHandler.handle(new DeclineInvitationCommand(pending, roomId)))
                .isInstanceOf(RunningInvitationNotPendingException.class);
    }

    @Test
    @DisplayName("혼자인 방에서 만든 사람이 나가 방이 취소되면 대기 중인 초대가 만료된다")
    void lastLeaveExpiresPendingInvitations() {
        // given
        UUID host = onboardedUser();
        UUID friend = friendOf(host);
        Long roomId = open(host);
        invite(host, roomId, friend);

        // when
        cancelHandler.handle(new CancelMatchCommand(host));

        // then
        RunningRoom room = runningStore.findRoom(roomId).orElseThrow();
        assertThat(room.getStatus()).isEqualTo(RunningRoomStatus.CANCELLED);
        assertThat(room.invitationStatusOf(new UserId(friend))).contains(RoomInvitationStatus.EXPIRED);
        assertThatThrownBy(() -> accept(friend, roomId)).isInstanceOf(RunningInvitationClosedException.class);
    }

    private Long open(UUID userId) {
        return openHandler.handle(new OpenInviteRoomCommand(userId, startAt, TARGET_DISTANCE)).runningRoomId();
    }

    // 한 번에 한 명이다 — 여러 명은 사람마다 부른다
    private void invite(UUID inviter, Long roomId, UUID... invitees) {
        for (UUID invitee : invitees) {
            inviteHandler.handle(new SendInvitationCommand(inviter, roomId, invitee));
        }
    }

    private int inviteCountOf(Long roomId, UUID inviter, UUID invitee) {
        return runningStore.findRoom(roomId).orElseThrow().getInvitations().stream()
                .filter(invitation -> invitation.isFor(new UserId(invitee)) && invitation.isSentBy(new UserId(inviter)))
                .findFirst()
                .orElseThrow()
                .getInviteCount()
                .value();
    }

    private void accept(UUID invitee, Long roomId) {
        acceptHandler.handle(new AcceptInvitationCommand(invitee, roomId));
    }

    private UUID friendOf(UUID userId) {
        UUID friend = onboardedUser();
        relationshipStore.seedFriend(userId, friend);
        return friend;
    }

    private UUID onboardedUser() {
        userSequence++;
        UUID userId = signUpHandler.handle(new SignUpCommand(
                issueVerificationTicket("inviter" + userSequence + "@runiverse.com"), PASSWORD)).userId();
        completeOnboardingHandler.handle(new CompleteOnboardingCommand(
                userId, "초대러너" + userSequence, "MALE", LocalDate.of(1998, 5, 20),
                AVG_PACE, new BigDecimal("70.0"), new BigDecimal("175.0")));
        return userId;
    }

    private RecordingMatchStreamConnection connect(UUID userId, Long runningRoomId) {
        RecordingMatchStreamConnection connection = new RecordingMatchStreamConnection("conn-" + userId);
        matchStreamRegistry.register(new UserId(userId), connection);
        matchRoomMemberRegistry.join(new UserId(userId), runningRoomId);
        return connection;
    }

    private static List<RoomInvitationStatus> statusesOf(RunningRoom room, UUID invitee) {
        return room.getInvitations().stream()
                .filter(invitation -> invitation.isFor(new UserId(invitee)))
                .map(RoomInvitation::getStatus)
                .toList();
    }

    // 초대 방 갱신은 매칭 방 이벤트가 아니라 INVITE_ROOM_UPDATED로 간다
    private static RoomInfo lastInviteRoomOf(RecordingMatchStreamConnection connection) {
        List<MatchStreamEvent> received = connection.received();
        assertThat(received).isNotEmpty();
        MatchStreamEvent last = received.getLast();
        assertThat(last.type()).isEqualTo(MatchEventType.INVITE_ROOM_UPDATED);
        return last.room();
    }
}
