package com.runiverse.running_service.unit_test.running.domain.room;

import com.github.f4b6a3.uuid.UuidCreator;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.running.player.vo.RunningPlayerId;
import com.runiverse.running_service.domain.running.room.InvitationDraft;
import com.runiverse.running_service.domain.running.room.RoomInvitation;
import com.runiverse.running_service.domain.running.room.RunningRoom;
import com.runiverse.running_service.domain.running.room.SessionDraft;
import com.runiverse.running_service.domain.running.room.exception.AlreadyRoomPlayerException;
import com.runiverse.running_service.domain.running.room.exception.InvalidInvitationStatusTransitionException;
import com.runiverse.running_service.domain.running.room.exception.InvalidInviteCountException;
import com.runiverse.running_service.domain.running.room.exception.InviteCountReachedException;
import com.runiverse.running_service.domain.running.room.exception.NotInvitedException;
import com.runiverse.running_service.domain.running.room.exception.NotRoomPlayerException;
import com.runiverse.running_service.domain.running.room.exception.RoomNotInvitableException;
import com.runiverse.running_service.domain.running.room.exception.RoomNotJoinableException;
import com.runiverse.running_service.domain.running.room.exception.SelfInvitationException;
import com.runiverse.running_service.domain.running.room.vo.RoomInvitationStatus;
import com.runiverse.running_service.domain.running.room.vo.RunningRoomStatus;
import com.runiverse.running_service.domain.running.room.vo.RunningRoomType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("러닝방 초대 단위 테스트")
public class RunningRoomInvitationTest {

    private static final LocalDateTime START = LocalDateTime.of(2026, 8, 20, 6, 0);
    private static final LocalDateTime LEFT_AT = START.minusMinutes(30);
    private static final int PACE = 330;
    private static final int TARGET_DISTANCE = 5_000;
    private static final int MAX_INVITE_COUNT = 5;

    private static final UserId HOST = newUser();          // 방을 연 사람 — 초대로 들어오지 않았다
    private static final UserId MEMBER = newUser();        // 초대를 수락해 방에 있는 사람
    private static final UserId INVITEE = newUser();
    private static final UserId STRANGER = newUser();      // 이 방 참가자가 아니다
    private static final RunningPlayerId HOST_PLAYER = new RunningPlayerId(1L);
    private static final RunningPlayerId MEMBER_PLAYER = new RunningPlayerId(2L);
    private static final RunningPlayerId INVITEE_PLAYER = new RunningPlayerId(3L);

    private static UserId newUser() {
        return new UserId(UuidCreator.getTimeOrderedEpoch());
    }

    // 만든 사람 혼자인 새 초대 방
    private static RunningRoom openedRoom() {
        return RunningRoom.openInvite(HOST, HOST_PLAYER, PACE, TARGET_DISTANCE, START);
    }

    // DB에서 복원한 초대 방 — 각 테스트는 세션·초대만 바꿔 조립한다
    private static RunningRoom restored(RunningRoomStatus status, List<SessionDraft> sessions,
                                        List<InvitationDraft> invitations) {
        int connected = (int) sessions.stream().filter(SessionDraft::connected).count();
        return RunningRoom.builder()
                .runningRoomId(130L)
                .type(RunningRoomType.INVITE)
                .status(status)
                .startAt(START)
                .targetDistance(TARGET_DISTANCE)
                .avgPace(PACE)
                .currentPlayerCount(connected)
                .maxPlayerCount(4)
                .sessions(sessions)
                .invitations(invitations)
                .build();
    }

    private static RunningRoom recruiting(List<InvitationDraft> invitations) {
        return restored(RunningRoomStatus.MATCHING, List.of(
                connected(HOST, HOST_PLAYER), connected(MEMBER, MEMBER_PLAYER)), invitations);
    }

    private static SessionDraft connected(UserId userId, RunningPlayerId playerId) {
        return new SessionDraft(userId, playerId, 0, true);
    }

    private static InvitationDraft sent(UserId inviter, UserId invitee,
                                        RoomInvitationStatus status, int count) {
        return new InvitationDraft(inviter, invitee, status, count);
    }

    private static List<RoomInvitationStatus> statusesOf(RunningRoom room, UserId invitee) {
        return room.getInvitations().stream()
                .filter(invitation -> invitation.isFor(invitee))
                .map(RoomInvitation::getStatus)
                .toList();
    }

    private static RoomInvitation rowOf(RunningRoom room, UserId inviter, UserId invitee) {
        return room.getInvitations().stream()
                .filter(invitation -> invitation.isFor(invitee) && invitation.isSentBy(inviter))
                .findFirst()
                .orElseThrow();
    }

    @Nested
    @DisplayName("복원 테스트")
    class RestoreTest {

        @Test
        @DisplayName("저장된 초대 행을 보낸 사람별로 그대로 복원한다")
        void restoresRows() {
            // when
            RunningRoom room = recruiting(List.of(
                    sent(HOST, INVITEE, RoomInvitationStatus.DECLINED, 2),
                    sent(MEMBER, INVITEE, RoomInvitationStatus.DECLINED, 1)));

            // then
            assertThat(room.getInvitations()).hasSize(2);
            assertThat(rowOf(room, HOST, INVITEE).getInviteCount().value()).isEqualTo(2);
            assertThat(room.invitationStatusOf(INVITEE)).contains(RoomInvitationStatus.DECLINED);
        }

        @Test
        @DisplayName("초대가 없으면 빈 목록이고 받은 사람의 상태도 없다")
        void noInvitations() {
            // when
            RunningRoom room = openedRoom();

            // then -> 매칭·솔로 방도 이 경로라 기존 호출부가 초대를 몰라도 된다
            assertThat(room.getInvitations()).isEmpty();
            assertThat(room.invitationStatusOf(INVITEE)).isEmpty();
        }

        @Test
        @DisplayName("자기 자신에게 보낸 행은 복원할 수 없다")
        void rejectsSelfRow() {
            // when & then -> DB CHECK와 같은 규칙
            assertThatThrownBy(() -> recruiting(List.of(
                    sent(INVITEE, INVITEE, RoomInvitationStatus.PENDING, 1))))
                    .isInstanceOf(SelfInvitationException.class);
        }

        @Test
        @DisplayName("보낸 횟수가 1 미만인 행은 복원할 수 없다")
        void rejectsZeroCount() {
            // when & then
            assertThatThrownBy(() -> recruiting(List.of(
                    sent(HOST, INVITEE, RoomInvitationStatus.PENDING, 0))))
                    .isInstanceOf(InvalidInviteCountException.class);
        }

        @Test
        @DisplayName("밖에서 받은 초대 목록은 바꿀 수 없다")
        void invitationsAreReadOnly() {
            // given
            RunningRoom room = openedRoom();

            // when & then -> 행은 방을 거쳐야만 생긴다
            assertThatThrownBy(() -> room.getInvitations().clear())
                    .isInstanceOf(UnsupportedOperationException.class);
        }
    }

    @Nested
    @DisplayName("초대 테스트")
    class InviteTest {

        @Test
        @DisplayName("처음 보내면 대기 상태의 행이 1회로 생긴다")
        void firstInviteOpensPendingRow() {
            // given
            RunningRoom room = openedRoom();

            // when
            room.invite(HOST, INVITEE, MAX_INVITE_COUNT);

            // then
            RoomInvitation invitation = rowOf(room, HOST, INVITEE);
            assertThat(invitation.getInviterId()).isEqualTo(HOST);
            assertThat(invitation.getInviteeId()).isEqualTo(INVITEE);
            assertThat(invitation.getStatus()).isEqualTo(RoomInvitationStatus.PENDING);
            assertThat(invitation.getInviteCount().value()).isEqualTo(1);
            assertThat(room.getInvitations()).hasSize(1);
        }

        @Test
        @DisplayName("대기 중에 다시 보내면 행이 늘지 않고 횟수만 오른다")
        void resendWhilePendingIncreasesCount() {
            // given
            RunningRoom room = recruiting(List.of(sent(HOST, INVITEE, RoomInvitationStatus.PENDING, 2)));

            // when -> 독촉
            room.invite(HOST, INVITEE, MAX_INVITE_COUNT);

            // then
            assertThat(rowOf(room, HOST, INVITEE).getInviteCount().value()).isEqualTo(3);
            assertThat(room.getInvitations()).hasSize(1);
            assertThat(room.invitationStatusOf(INVITEE)).contains(RoomInvitationStatus.PENDING);
        }

        @Test
        @DisplayName("다른 참가자가 보내면 그 사람의 행이 따로 생긴다")
        void otherInviterOpensOwnRow() {
            // given
            RunningRoom room = recruiting(List.of(sent(HOST, INVITEE, RoomInvitationStatus.PENDING, 1)));

            // when -> 방장이 없어 누구나 부를 수 있다
            room.invite(MEMBER, INVITEE, MAX_INVITE_COUNT);

            // then
            assertThat(room.getInvitations()).hasSize(2);
            assertThat(rowOf(room, MEMBER, INVITEE).getInviteCount().value()).isEqualTo(1);
            assertThat(rowOf(room, HOST, INVITEE).getInviteCount().value()).isEqualTo(1);
        }

        @ParameterizedTest(name = "{0}")
        @EnumSource(value = RoomInvitationStatus.class, names = {"DECLINED", "LEFT"})
        @DisplayName("거절했거나 나간 사람을 부르면 다른 사람의 행까지 함께 대기로 되살아나고 횟수는 이어서 센다")
        void reinviteRevivesAllRows(RoomInvitationStatus status) {
            // given
            RunningRoom room = recruiting(List.of(
                    sent(HOST, INVITEE, status, 2),
                    sent(MEMBER, INVITEE, status, 1)));

            // when
            room.invite(HOST, INVITEE, MAX_INVITE_COUNT);

            // then
            assertThat(rowOf(room, HOST, INVITEE).getInviteCount().value()).isEqualTo(3);
            assertThat(statusesOf(room, INVITEE)).containsOnly(RoomInvitationStatus.PENDING);
            assertThat(rowOf(room, MEMBER, INVITEE).getInviteCount().value()).isEqualTo(1);
        }

        @Test
        @DisplayName("거절한 사람을 새 보낸 사람이 부르면 새 행도 대기로 생기고 기존 행도 되살아난다")
        void newInviterRevivesDeclined() {
            // given
            RunningRoom room = recruiting(List.of(sent(HOST, INVITEE, RoomInvitationStatus.DECLINED, 1)));

            // when
            room.invite(MEMBER, INVITEE, MAX_INVITE_COUNT);

            // then -> 새 행은 지금 상태를 따른다
            assertThat(statusesOf(room, INVITEE)).hasSize(2)
                    .containsOnly(RoomInvitationStatus.PENDING);
        }

        @Test
        @DisplayName("보낸 횟수가 상한에 닿으면 막고 상태도 바꾸지 않는다")
        void rejectsWhenCountReached() {
            // given
            RunningRoom room = recruiting(List.of(
                    sent(HOST, INVITEE, RoomInvitationStatus.DECLINED, MAX_INVITE_COUNT)));

            // when & then -> 거절당한 뒤 계속 보내는 것을 막는 게 상한의 목적이다. 애플리케이션이 409로 먼저 거른다
            assertThat(room.hasReachedInviteLimit(HOST, INVITEE, MAX_INVITE_COUNT)).isTrue();
            assertThatThrownBy(() -> room.invite(HOST, INVITEE, MAX_INVITE_COUNT))
                    .isInstanceOf(InviteCountReachedException.class);
            assertThat(rowOf(room, HOST, INVITEE).getInviteCount().value()).isEqualTo(MAX_INVITE_COUNT);
            assertThat(room.invitationStatusOf(INVITEE)).contains(RoomInvitationStatus.DECLINED);
        }

        @Test
        @DisplayName("상한은 보낸 사람마다 센다 — 다른 참가자는 보낼 수 있다")
        void countIsPerInviter() {
            // given
            RunningRoom room = recruiting(List.of(
                    sent(HOST, INVITEE, RoomInvitationStatus.PENDING, MAX_INVITE_COUNT)));

            // when
            room.invite(MEMBER, INVITEE, MAX_INVITE_COUNT);

            // then
            assertThat(room.hasReachedInviteLimit(MEMBER, INVITEE, MAX_INVITE_COUNT)).isFalse();
            assertThat(rowOf(room, MEMBER, INVITEE).getInviteCount().value()).isEqualTo(1);
        }

        @Test
        @DisplayName("아직 보낸 적 없으면 상한에 닿지 않았다")
        void noRowHasNotReachedLimit() {
            // when & then
            assertThat(openedRoom().hasReachedInviteLimit(HOST, INVITEE, MAX_INVITE_COUNT)).isFalse();
        }

        @Test
        @DisplayName("이미 방에 있는 사람은 막는다")
        void rejectsMember() {
            // given
            RunningRoom room = recruiting(List.of());

            // when & then -> 애플리케이션이 isInRoom으로 409를 먼저 낸다
            assertThat(room.isInRoom(MEMBER)).isTrue();
            assertThatThrownBy(() -> room.invite(HOST, MEMBER, MAX_INVITE_COUNT))
                    .isInstanceOf(AlreadyRoomPlayerException.class);
            assertThat(room.getInvitations()).isEmpty();
        }

        @Test
        @DisplayName("나간 세션은 방에 있는 것이 아니다")
        void leftSessionIsNotInRoom() {
            // given
            RunningRoom room = restored(RunningRoomStatus.MATCHING, List.of(
                    connected(HOST, HOST_PLAYER), new SessionDraft(MEMBER, MEMBER_PLAYER, 1, false)), List.of());

            // when & then
            assertThat(room.isInRoom(MEMBER)).isFalse();
            assertThat(room.isInRoom(STRANGER)).isFalse();
        }

        @Test
        @DisplayName("매칭 방에서는 초대할 수 없다")
        void rejectsMatchRoom() {
            // given
            RunningRoom room = RunningRoom.openMatch(HOST, HOST_PLAYER, PACE, TARGET_DISTANCE, START);

            // when & then
            assertThatThrownBy(() -> room.invite(HOST, INVITEE, MAX_INVITE_COUNT))
                    .isInstanceOf(RoomNotInvitableException.class);
        }

        @Test
        @DisplayName("모집이 끝난 방에서는 초대할 수 없다")
        void rejectsClosedRoom() {
            // given
            RunningRoom room = restored(RunningRoomStatus.MATCHED,
                    List.of(connected(HOST, HOST_PLAYER)), List.of());

            // when & then
            assertThatThrownBy(() -> room.invite(HOST, INVITEE, MAX_INVITE_COUNT))
                    .isInstanceOf(RoomNotInvitableException.class);
        }

        @Test
        @DisplayName("이 방 참가자가 아니면 초대할 수 없다")
        void rejectsStranger() {
            // given
            RunningRoom room = openedRoom();

            // when & then
            assertThatThrownBy(() -> room.invite(STRANGER, INVITEE, MAX_INVITE_COUNT))
                    .isInstanceOf(NotRoomPlayerException.class);
        }

        @Test
        @DisplayName("방을 나간 사람은 초대할 수 없다")
        void rejectsLeftInviter() {
            // given
            RunningRoom room = restored(RunningRoomStatus.MATCHING, List.of(
                    connected(HOST, HOST_PLAYER), new SessionDraft(MEMBER, MEMBER_PLAYER, 1, false)), List.of());

            // when & then
            assertThatThrownBy(() -> room.invite(MEMBER, INVITEE, MAX_INVITE_COUNT))
                    .isInstanceOf(NotRoomPlayerException.class);
        }

        @Test
        @DisplayName("자기 자신은 초대할 수 없다")
        void rejectsSelf() {
            // given
            RunningRoom room = openedRoom();

            // when & then
            assertThatThrownBy(() -> room.invite(HOST, HOST, MAX_INVITE_COUNT))
                    .isInstanceOf(SelfInvitationException.class);
        }
    }

    @Nested
    @DisplayName("수락 테스트")
    class AcceptTest {

        @Test
        @DisplayName("수락하면 자리를 잡고 누가 보냈든 받은 행이 모두 수락으로 바뀐다")
        void acceptJoinsAndAcceptsAllRows() {
            // given
            RunningRoom room = recruiting(List.of(
                    sent(HOST, INVITEE, RoomInvitationStatus.PENDING, 1),
                    sent(MEMBER, INVITEE, RoomInvitationStatus.PENDING, 3)));

            // when
            room.acceptInvitation(INVITEE, INVITEE_PLAYER);

            // then
            assertThat(statusesOf(room, INVITEE)).containsOnly(RoomInvitationStatus.ACCEPTED);
            assertThat(room.getPlayerCount().current()).isEqualTo(3);
            assertThat(room.getSessions()).anySatisfy(session -> {
                assertThat(session.isSameUser(INVITEE)).isTrue();
                assertThat(session.isSamePlayer(INVITEE_PLAYER)).isTrue();
                assertThat(session.isConnected()).isTrue();
            });
        }

        @Test
        @DisplayName("다른 사람이 받은 초대는 건드리지 않는다")
        void leavesOtherInvitees() {
            // given
            UserId another = newUser();
            RunningRoom room = recruiting(List.of(
                    sent(HOST, INVITEE, RoomInvitationStatus.PENDING, 1),
                    sent(HOST, another, RoomInvitationStatus.PENDING, 1)));

            // when
            room.acceptInvitation(INVITEE, INVITEE_PLAYER);

            // then
            assertThat(room.invitationStatusOf(another)).contains(RoomInvitationStatus.PENDING);
        }

        @Test
        @DisplayName("받은 초대가 없으면 수락할 수 없다")
        void rejectsNotInvited() {
            // given
            RunningRoom room = recruiting(List.of());

            // when & then
            assertThatThrownBy(() -> room.acceptInvitation(INVITEE, INVITEE_PLAYER))
                    .isInstanceOf(NotInvitedException.class);
            assertThat(room.getPlayerCount().current()).isEqualTo(2);
        }

        @ParameterizedTest(name = "{0}")
        @EnumSource(value = RoomInvitationStatus.class, names = {"DECLINED", "LEFT", "EXPIRED"})
        @DisplayName("대기 중이 아닌 초대는 수락할 수 없고 자리도 잡지 않는다")
        void rejectsNonPending(RoomInvitationStatus status) {
            // given -> 거절 뒤 들어가려면 다시 초대받아야 한다
            RunningRoom room = recruiting(List.of(sent(HOST, INVITEE, status, 1)));

            // when & then
            assertThatThrownBy(() -> room.acceptInvitation(INVITEE, INVITEE_PLAYER))
                    .isInstanceOf(InvalidInvitationStatusTransitionException.class);
            assertThat(room.getPlayerCount().current()).isEqualTo(2);
            assertThat(room.getSessions()).noneMatch(session -> session.isSameUser(INVITEE));
        }

        @Test
        @DisplayName("자리가 다 찼으면 수락할 수 없고 초대는 대기로 남는다")
        void fullRoomKeepsPending() {
            // given
            RunningRoom room = restored(RunningRoomStatus.MATCHING, List.of(
                    connected(HOST, HOST_PLAYER),
                    connected(MEMBER, MEMBER_PLAYER),
                    connected(newUser(), new RunningPlayerId(4L)),
                    connected(newUser(), new RunningPlayerId(5L))),
                    List.of(sent(HOST, INVITEE, RoomInvitationStatus.PENDING, 1)));

            // when & then -> 누가 나가면 다시 수락할 수 있다
            assertThatThrownBy(() -> room.acceptInvitation(INVITEE, INVITEE_PLAYER))
                    .isInstanceOf(RoomNotJoinableException.class);
            assertThat(room.invitationStatusOf(INVITEE)).contains(RoomInvitationStatus.PENDING);
        }

        @Test
        @DisplayName("모집이 끝난 방에서는 수락할 수 없고 초대는 그대로다")
        void closedRoomKeepsPending() {
            // given
            RunningRoom room = restored(RunningRoomStatus.MATCHED, List.of(connected(HOST, HOST_PLAYER)),
                    List.of(sent(HOST, INVITEE, RoomInvitationStatus.PENDING, 1)));

            // when & then
            assertThatThrownBy(() -> room.acceptInvitation(INVITEE, INVITEE_PLAYER))
                    .isInstanceOf(RoomNotJoinableException.class);
            assertThat(room.invitationStatusOf(INVITEE)).contains(RoomInvitationStatus.PENDING);
        }

        @Test
        @DisplayName("나갔다가 다시 초대받아 수락하면 세션을 되살려 새 신청을 꽂는다")
        void reacceptReassignsSession() {
            // given -> 나갈 때 LEFT가 됐고, 다시 초대로 PENDING이 됐다
            RunningPlayerId newPlayer = new RunningPlayerId(9L);
            RunningRoom room = restored(RunningRoomStatus.MATCHING, List.of(
                    connected(HOST, HOST_PLAYER), new SessionDraft(INVITEE, INVITEE_PLAYER, 1, false)),
                    List.of(sent(HOST, INVITEE, RoomInvitationStatus.PENDING, 2)));

            // when
            room.acceptInvitation(INVITEE, newPlayer);

            // then
            assertThat(room.getSessions()).hasSize(2)
                    .anySatisfy(session -> {
                        assertThat(session.isSameUser(INVITEE)).isTrue();
                        assertThat(session.isSamePlayer(newPlayer)).isTrue();
                        assertThat(session.isConnected()).isTrue();
                    });
            assertThat(room.invitationStatusOf(INVITEE)).contains(RoomInvitationStatus.ACCEPTED);
        }
    }

    @Nested
    @DisplayName("거절 테스트")
    class DeclineTest {

        @Test
        @DisplayName("거절하면 받은 행이 모두 거절로 바뀌고 지워지지 않는다")
        void declineAllRows() {
            // given
            RunningRoom room = recruiting(List.of(
                    sent(HOST, INVITEE, RoomInvitationStatus.PENDING, 1),
                    sent(MEMBER, INVITEE, RoomInvitationStatus.PENDING, 1)));

            // when
            room.declineInvitation(INVITEE);

            // then -> 방 화면이 "거절"을 보여야 한다
            assertThat(statusesOf(room, INVITEE)).hasSize(2)
                    .containsOnly(RoomInvitationStatus.DECLINED);
            assertThat(room.getPlayerCount().current()).isEqualTo(2);
        }

        @Test
        @DisplayName("받은 초대가 없으면 거절할 수 없다")
        void rejectsNotInvited() {
            // given
            RunningRoom room = recruiting(List.of());

            // when & then
            assertThatThrownBy(() -> room.declineInvitation(INVITEE))
                    .isInstanceOf(NotInvitedException.class);
        }

        @ParameterizedTest(name = "{0}")
        @EnumSource(value = RoomInvitationStatus.class, names = {"ACCEPTED", "DECLINED", "LEFT", "EXPIRED"})
        @DisplayName("대기 중이 아닌 초대는 거절할 수 없고 어느 행도 바뀌지 않는다")
        void rejectsNonPending(RoomInvitationStatus status) {
            // given
            RunningRoom room = recruiting(List.of(
                    sent(HOST, INVITEE, status, 1),
                    sent(MEMBER, INVITEE, status, 1)));

            // when & then
            assertThatThrownBy(() -> room.declineInvitation(INVITEE))
                    .isInstanceOf(InvalidInvitationStatusTransitionException.class);
            assertThat(statusesOf(room, INVITEE)).containsOnly(status);
        }
    }

    @Nested
    @DisplayName("나가기 테스트")
    class LeaveTest {

        @Test
        @DisplayName("초대로 들어온 사람이 나가면 받은 행이 모두 나감으로 바뀐다")
        void acceptedBecomesLeft() {
            // given
            RunningRoom room = recruiting(List.of(
                    sent(HOST, MEMBER, RoomInvitationStatus.ACCEPTED, 1),
                    sent(newUser(), MEMBER, RoomInvitationStatus.ACCEPTED, 1)));

            // when
            room.leave(MEMBER, LEFT_AT);

            // then -> ACCEPTED는 "지금 방에 있음"만 뜻한다
            assertThat(statusesOf(room, MEMBER)).containsOnly(RoomInvitationStatus.LEFT);
        }

        @Test
        @DisplayName("나간 사람은 다시 초대받을 수 있다")
        void leftCanBeReinvited() {
            // given
            RunningRoom room = recruiting(List.of(sent(HOST, MEMBER, RoomInvitationStatus.ACCEPTED, 1)));
            room.leave(MEMBER, LEFT_AT);

            // when
            room.invite(HOST, MEMBER, MAX_INVITE_COUNT);

            // then
            assertThat(rowOf(room, HOST, MEMBER).getInviteCount().value()).isEqualTo(2);
            assertThat(room.invitationStatusOf(MEMBER)).contains(RoomInvitationStatus.PENDING);
        }

        @Test
        @DisplayName("초대 없이 들어온 사람이 나가도 다른 사람의 초대는 그대로다")
        void hostLeaveKeepsInvitations() {
            // given
            RunningRoom room = recruiting(List.of(sent(HOST, INVITEE, RoomInvitationStatus.PENDING, 1)));

            // when
            room.leave(HOST, LEFT_AT);

            // then
            assertThat(room.invitationStatusOf(INVITEE)).contains(RoomInvitationStatus.PENDING);
            assertThat(room.invitationStatusOf(HOST)).isEmpty();
        }
    }

    @Nested
    @DisplayName("만료 테스트")
    class ExpireTest {

        private final UserId declined = newUser();
        private final UserId left = newUser();

        // 대기 중 2행(보낸 사람 둘)·거절·나감·수락해 방에 있음이 섞인 방
        private RunningRoom mixedRoom() {
            return recruiting(List.of(
                    sent(HOST, INVITEE, RoomInvitationStatus.PENDING, 1),
                    sent(MEMBER, INVITEE, RoomInvitationStatus.PENDING, 2),
                    sent(HOST, declined, RoomInvitationStatus.DECLINED, 1),
                    sent(HOST, left, RoomInvitationStatus.LEFT, 1),
                    sent(HOST, MEMBER, RoomInvitationStatus.ACCEPTED, 1)));
        }

        @Test
        @DisplayName("모집이 마감되면 대기 중인 초대만 보낸 사람과 상관없이 모두 만료된다")
        void closeExpiresOnlyPending() {
            // given
            RunningRoom room = mixedRoom();

            // when
            room.closeMatching();

            // then -> 거절·나감은 방 화면 기록이라 그대로 남고, 방에 있는 사람도 그대로다
            assertThat(statusesOf(room, INVITEE)).hasSize(2).containsOnly(RoomInvitationStatus.EXPIRED);
            assertThat(room.invitationStatusOf(declined)).contains(RoomInvitationStatus.DECLINED);
            assertThat(room.invitationStatusOf(left)).contains(RoomInvitationStatus.LEFT);
            assertThat(room.invitationStatusOf(MEMBER)).contains(RoomInvitationStatus.ACCEPTED);
        }

        @Test
        @DisplayName("마지막 참가자가 나가 방이 취소되면 대기 중인 초대가 만료된다")
        void lastLeaveExpiresPending() {
            // given -> 만든 사람 혼자인 방
            RunningRoom room = restored(RunningRoomStatus.MATCHING, List.of(connected(HOST, HOST_PLAYER)),
                    List.of(sent(HOST, INVITEE, RoomInvitationStatus.PENDING, 1)));

            // when
            room.leave(HOST, LEFT_AT);

            // then
            assertThat(room.getStatus()).isEqualTo(RunningRoomStatus.CANCELLED);
            assertThat(room.invitationStatusOf(INVITEE)).contains(RoomInvitationStatus.EXPIRED);
        }

        @Test
        @DisplayName("초대로 들어온 마지막 사람이 나가면 방이 취소되고 본인은 나감, 남은 대기 초대는 만료된다")
        void lastInviteeLeaveExpiresOthers() {
            // given -> 만든 사람은 먼저 나갔고 초대로 들어온 사람만 남았다
            RunningRoom room = restored(RunningRoomStatus.MATCHING,
                    List.of(new SessionDraft(HOST, HOST_PLAYER, 1, false), connected(MEMBER, MEMBER_PLAYER)),
                    List.of(sent(HOST, MEMBER, RoomInvitationStatus.ACCEPTED, 1),
                            sent(HOST, INVITEE, RoomInvitationStatus.PENDING, 1)));

            // when
            room.leave(MEMBER, LEFT_AT);

            // then
            assertThat(room.getStatus()).isEqualTo(RunningRoomStatus.CANCELLED);
            assertThat(room.invitationStatusOf(MEMBER)).contains(RoomInvitationStatus.LEFT);
            assertThat(room.invitationStatusOf(INVITEE)).contains(RoomInvitationStatus.EXPIRED);
        }

        @Test
        @DisplayName("사람이 남아 있으면 나가도 대기 중인 초대는 그대로다")
        void leaveWithOthersKeepsPending() {
            // given
            RunningRoom room = recruiting(List.of(sent(HOST, INVITEE, RoomInvitationStatus.PENDING, 1)));

            // when
            room.leave(MEMBER, LEFT_AT);

            // then -> 모집은 계속된다
            assertThat(room.getStatus()).isEqualTo(RunningRoomStatus.MATCHING);
            assertThat(room.invitationStatusOf(INVITEE)).contains(RoomInvitationStatus.PENDING);
        }

        @Test
        @DisplayName("사람이 남아 있으면 다른 사람이 나가도 수락·거절·나감은 그대로다")
        void leaveWithOthersKeepsAnswered() {
            // given
            RunningRoom room = mixedRoom();

            // when -> 초대 없이 들어온 사람이 나간다
            room.leave(HOST, LEFT_AT);

            // then
            assertThat(room.invitationStatusOf(MEMBER)).contains(RoomInvitationStatus.ACCEPTED);
            assertThat(room.invitationStatusOf(declined)).contains(RoomInvitationStatus.DECLINED);
            assertThat(room.invitationStatusOf(left)).contains(RoomInvitationStatus.LEFT);
        }

        @Test
        @DisplayName("방을 취소하면 대기 중인 초대가 만료된다")
        void cancelExpiresPending() {
            // given
            RunningRoom room = mixedRoom();

            // when
            room.cancel(LEFT_AT);

            // then
            assertThat(statusesOf(room, INVITEE)).containsOnly(RoomInvitationStatus.EXPIRED);
            assertThat(room.invitationStatusOf(declined)).contains(RoomInvitationStatus.DECLINED);
        }

        @Test
        @DisplayName("시작한 방을 취소해도 수락·거절·나감·만료는 그대로다")
        void cancelStartedRoomKeepsAnswered() {
            // given -> 시작한 방이라 대기 중이던 초대는 마감 때 이미 만료됐다
            UserId expired = newUser();
            RunningRoom room = restored(RunningRoomStatus.STARTED,
                    List.of(connected(HOST, HOST_PLAYER), connected(MEMBER, MEMBER_PLAYER)),
                    List.of(sent(HOST, MEMBER, RoomInvitationStatus.ACCEPTED, 1),
                            sent(HOST, declined, RoomInvitationStatus.DECLINED, 1),
                            sent(HOST, left, RoomInvitationStatus.LEFT, 1),
                            sent(HOST, expired, RoomInvitationStatus.EXPIRED, 1)));

            // when
            room.cancel(START.plusMinutes(30));

            // then
            assertThat(room.getStatus()).isEqualTo(RunningRoomStatus.CANCELLED);
            assertThat(room.invitationStatusOf(MEMBER)).contains(RoomInvitationStatus.ACCEPTED);
            assertThat(room.invitationStatusOf(declined)).contains(RoomInvitationStatus.DECLINED);
            assertThat(room.invitationStatusOf(left)).contains(RoomInvitationStatus.LEFT);
            assertThat(room.invitationStatusOf(expired)).contains(RoomInvitationStatus.EXPIRED);
        }

        @Test
        @DisplayName("만료된 초대는 수락·거절·다시 초대 모두 할 수 없다")
        void expiredIsFinal() {
            // given -> 마감 뒤라 방도 더 이상 모집하지 않는다
            RunningRoom room = mixedRoom();
            room.closeMatching();

            // when & then
            assertThatThrownBy(() -> room.acceptInvitation(INVITEE, INVITEE_PLAYER))
                    .isInstanceOf(InvalidInvitationStatusTransitionException.class);
            assertThatThrownBy(() -> room.declineInvitation(INVITEE))
                    .isInstanceOf(InvalidInvitationStatusTransitionException.class);
            assertThatThrownBy(() -> room.invite(HOST, INVITEE, MAX_INVITE_COUNT))
                    .isInstanceOf(RoomNotInvitableException.class);
        }

        @Test
        @DisplayName("초대가 없는 방도 마감·취소가 그대로 된다")
        void noInvitationsIsNoop() {
            // given -> 매칭 방은 초대가 없다
            RunningRoom room = RunningRoom.openMatch(HOST, HOST_PLAYER, PACE, TARGET_DISTANCE, START);

            // when
            room.closeMatching();

            // then
            assertThat(room.getStatus()).isEqualTo(RunningRoomStatus.MATCHED);
            assertThat(room.getInvitations()).isEmpty();
        }
    }
}
