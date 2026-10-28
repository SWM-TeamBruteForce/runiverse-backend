package com.runiverse.running_service.unit_test.match.application;

import com.github.f4b6a3.uuid.UuidCreator;
import com.runiverse.running_service.application.match.command.invitedecline.DeclineInvitationCommand;
import com.runiverse.running_service.application.match.command.invitedecline.DeclineInvitationHandler;
import com.runiverse.running_service.application.match.exception.InvitationNotFoundException;
import com.runiverse.running_service.application.match.exception.RunningInvitationNotPendingException;
import com.runiverse.running_service.application.match.port.out.LockMatchRoomPort;
import com.runiverse.running_service.application.match.port.out.UpdateMatchRoomPort;
import com.runiverse.running_service.application.match.common.MatchRoomChangedEvent;
import com.runiverse.running_service.application.match.common.RoomInfoAssembler;
import com.runiverse.running_service.application.match.port.out.RoomInfo;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.running.player.vo.RunningPlayerId;
import com.runiverse.running_service.domain.running.room.InvitationDraft;
import com.runiverse.running_service.domain.running.room.RoomInvitation;
import com.runiverse.running_service.domain.running.room.RunningRoom;
import com.runiverse.running_service.domain.running.room.SessionDraft;
import com.runiverse.running_service.domain.running.room.vo.RoomInvitationStatus;
import com.runiverse.running_service.domain.running.room.vo.RunningRoomId;
import com.runiverse.running_service.domain.running.room.vo.RunningRoomStatus;
import com.runiverse.running_service.domain.running.room.vo.RunningRoomType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@DisplayName("초대 거절 단위 테스트")
class DeclineInvitationHandlerTest {

    private static final long ROOM_ID = 130L;
    private static final LocalDateTime START = LocalDateTime.now().plusHours(1).withSecond(0).withNano(0);

    @Mock
    private LockMatchRoomPort lockMatchRoomPort;

    @Mock
    private UpdateMatchRoomPort updateMatchRoomPort;

    @Mock
    private RoomInfoAssembler roomInfoAssembler;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private DeclineInvitationHandler handler;
    private UserId host;
    private UserId invitee;

    @BeforeEach
    void setUp() {
        handler = new DeclineInvitationHandler(
                lockMatchRoomPort, updateMatchRoomPort, roomInfoAssembler, eventPublisher);
        host = newUser();
        invitee = newUser();
    }

    private static UserId newUser() {
        return new UserId(UuidCreator.getTimeOrderedEpoch());
    }

    private RunningRoom room(RunningRoomStatus status, List<InvitationDraft> invitations) {
        return RunningRoom.builder()
                .runningRoomId(ROOM_ID)
                .type(RunningRoomType.INVITE)
                .status(status)
                .startAt(START)
                .targetDistance(7_000)
                .avgPace(330)
                .currentPlayerCount(1)
                .maxPlayerCount(4)
                .sessions(List.of(new SessionDraft(host, new RunningPlayerId(1L), 0, true)))
                .invitations(invitations)
                .build();
    }

    private List<InvitationDraft> received(RoomInvitationStatus status) {
        return List.of(
                new InvitationDraft(host, invitee, status, 1),
                new InvitationDraft(newUser(), invitee, status, 3));
    }

    private void givenLocked(RunningRoom room) {
        given(lockMatchRoomPort.lockById(new RunningRoomId(ROOM_ID))).willReturn(Optional.of(room));
    }

    private void decline() {
        handler.handle(new DeclineInvitationCommand(invitee.value(), ROOM_ID));
    }

    @Nested
    @DisplayName("성공 테스트")
    class SuccessTest {

        @Test
        @DisplayName("거절하면 보낸 사람별 행이 모두 거절로 남고 저장한 뒤 갱신을 알린다")
        void declineInvitation() {
            // given
            RunningRoom room = room(RunningRoomStatus.MATCHING, received(RoomInvitationStatus.PENDING));
            givenLocked(room);
            given(roomInfoAssembler.assemble(any())).willReturn(new RoomInfo(
                    ROOM_ID, RunningRoomType.INVITE, RunningRoomStatus.MATCHING, START, START.minusMinutes(1), 7_000, 330, List.of(), List.of()));

            // when
            decline();

            // then -> 행을 지우지 않는다(친구 요청 거절과 다르다)
            assertThat(room.getInvitations()).hasSize(2)
                    .extracting(RoomInvitation::getStatus)
                    .containsOnly(RoomInvitationStatus.DECLINED);
            assertThat(room.getPlayerCount().current()).isEqualTo(1);
            verify(updateMatchRoomPort).update(room);
            verify(eventPublisher).publishEvent(any(MatchRoomChangedEvent.class));
        }

        @Test
        @DisplayName("모집이 끝난 방이어도 대기 중인 초대는 거절할 수 있다")
        void declineAfterClose() {
            // given -> 거절은 자리를 잡지 않아 마감과 무관하다
            RunningRoom room = room(RunningRoomStatus.MATCHED, received(RoomInvitationStatus.PENDING));
            givenLocked(room);
            given(roomInfoAssembler.assemble(any())).willReturn(new RoomInfo(
                    ROOM_ID, RunningRoomType.INVITE, RunningRoomStatus.MATCHED, START, START.minusMinutes(1), 7_000, 330, List.of(), List.of()));

            // when
            decline();

            // then
            assertThat(room.invitationStatusOf(invitee)).contains(RoomInvitationStatus.DECLINED);
        }
    }

    @Nested
    @DisplayName("실패 테스트")
    class FailureTest {

        @Test
        @DisplayName("방이 없으면 찾을 수 없다")
        void rejectsMissingRoom() {
            // given
            given(lockMatchRoomPort.lockById(new RunningRoomId(ROOM_ID))).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(DeclineInvitationHandlerTest.this::decline)
                    .isInstanceOf(InvitationNotFoundException.class);
            verifyNoInteractions(updateMatchRoomPort, eventPublisher);
        }

        @Test
        @DisplayName("이 방에서 나에게 온 초대가 없으면 찾을 수 없다")
        void rejectsNotInvited() {
            // given
            givenLocked(room(RunningRoomStatus.MATCHING, List.of()));

            // when & then
            assertThatThrownBy(DeclineInvitationHandlerTest.this::decline)
                    .isInstanceOf(InvitationNotFoundException.class);
            verifyNoInteractions(updateMatchRoomPort, eventPublisher);
        }

        @ParameterizedTest(name = "{0}")
        @EnumSource(value = RoomInvitationStatus.class, names = "PENDING", mode = EnumSource.Mode.EXCLUDE)
        @DisplayName("대기 중이 아닌 초대는 거절할 수 없고 어느 행도 바뀌지 않는다")
        void rejectsNonPending(RoomInvitationStatus status) {
            // given
            RunningRoom room = room(RunningRoomStatus.MATCHING, received(status));
            givenLocked(room);

            // when & then
            assertThatThrownBy(DeclineInvitationHandlerTest.this::decline)
                    .isInstanceOf(RunningInvitationNotPendingException.class);
            assertThat(room.getInvitations()).extracting(RoomInvitation::getStatus).containsOnly(status);
            verifyNoInteractions(updateMatchRoomPort, roomInfoAssembler, eventPublisher);
        }
    }
}
