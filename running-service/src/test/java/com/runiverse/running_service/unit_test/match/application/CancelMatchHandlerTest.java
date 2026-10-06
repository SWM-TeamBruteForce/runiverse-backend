package com.runiverse.running_service.unit_test.match.application;

import com.github.f4b6a3.uuid.UuidCreator;
import com.runiverse.running_service.application.match.command.cancel.CancelMatchCommand;
import com.runiverse.running_service.application.match.command.cancel.CancelMatchHandler;
import com.runiverse.running_service.application.match.common.MatchProperties;
import com.runiverse.running_service.application.match.common.MatchRoomChangedEvent;
import com.runiverse.running_service.application.match.common.RoomInfoAssembler;
import com.runiverse.running_service.application.match.exception.ActiveMatchNotFoundException;
import com.runiverse.running_service.application.match.exception.MatchAlreadyStartedException;
import com.runiverse.running_service.application.match.port.out.LockMatchApplicationPort;
import com.runiverse.running_service.application.match.port.out.LoadMatchRoomPort;
import com.runiverse.running_service.application.match.port.out.LockMatchRoomPort;
import com.runiverse.running_service.application.match.port.out.MatchCooldownPort;
import com.runiverse.running_service.application.match.port.out.MatchEventType;
import com.runiverse.running_service.application.match.port.out.MatchStreamEvent;
import com.runiverse.running_service.application.match.port.out.RoomInfo;
import com.runiverse.running_service.application.match.port.out.UpdateMatchApplicationPort;
import com.runiverse.running_service.application.match.port.out.UpdateMatchRoomPort;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.running.player.RunningPlayer;
import com.runiverse.running_service.domain.running.player.vo.RunningPlayerId;
import com.runiverse.running_service.domain.running.player.vo.RunningPlayerStatus;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@DisplayName("매칭 취소·나가기 단위 테스트")
class CancelMatchHandlerTest {

    private static final UUID USER_ID = UuidCreator.getTimeOrderedEpoch();
    private static final long PLAYER_ID = 7L;
    private static final long ROOM_ID = 125L;
    private static final int AVG_PACE = 360;
    private static final int TARGET_DISTANCE = 5_000;
    private static final Duration CLOSE_OFFSET = Duration.ofMinutes(10);
    private static final Duration READY_OFFSET = Duration.ofSeconds(10);
    private static final Duration FORCE_FINISH_OFFSET = Duration.ofHours(6);
    private static final int PACE_TIE_TOLERANCE = 10;
    private static final Duration COOLDOWN = Duration.ofMinutes(20);
    // 조립 결과는 이 테스트의 주제가 아니다 — 발행 여부만 본다
    private static final RoomInfo ROOM_INFO = new RoomInfo(
            ROOM_ID, RunningRoomStatus.MATCHING, LocalDateTime.now().plusHours(2),
            LocalDateTime.now().plusHours(1), TARGET_DISTANCE, AVG_PACE, List.of());

    @Mock
    private LockMatchApplicationPort lockMatchApplicationPort;

    @Mock
    private LoadMatchRoomPort loadMatchRoomPort;

    @Mock
    private LockMatchRoomPort lockMatchRoomPort;

    @Mock
    private UpdateMatchApplicationPort updateMatchApplicationPort;

    @Mock
    private UpdateMatchRoomPort updateMatchRoomPort;

    @Mock
    private MatchCooldownPort matchCooldownPort;

    @Mock
    private RoomInfoAssembler roomInfoAssembler;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private CancelMatchHandler cancelMatchHandler;

    @BeforeEach
    void setUp() {
        cancelMatchHandler = new CancelMatchHandler(
                lockMatchApplicationPort, loadMatchRoomPort, lockMatchRoomPort,
                updateMatchApplicationPort, updateMatchRoomPort, matchCooldownPort,
                new MatchProperties(CLOSE_OFFSET, READY_OFFSET, FORCE_FINISH_OFFSET,
                        PACE_TIE_TOLERANCE, COOLDOWN),
                roomInfoAssembler, eventPublisher);
    }

    @Test
    @DisplayName("모집 마감 전에 취소하면 제재가 없다")
    void cancelBeforeCloseIsNotPenalized() {
        // given -> 시작까지 2시간, 마감(start_at - 10분)은 한참 남았다
        givenActiveMatch(room(startAfter(Duration.ofHours(2)), 3));

        // when
        cancelMatchHandler.handle(new CancelMatchCommand(USER_ID));

        // then -> 취소도 status에 남는다. 제재 여부만 갈린다
        assertThat(leftPlayer().getStatus())
                .isEqualTo(RunningPlayerStatus.MATCHED_LEFT_NO_PENALTY);
        verifyNoInteractions(matchCooldownPort);
    }

    @Test
    @DisplayName("확정 후 2명 이상 남은 방에서 나가면 제재하고 쿨다운을 건다")
    void leaveAfterCloseIsPenalized() {
        // given -> 시작 5분 전이라 마감은 이미 지났고, 나 말고도 참가자가 있다
        givenActiveMatch(room(startAfter(Duration.ofMinutes(5)), 3));

        // when
        cancelMatchHandler.handle(new CancelMatchCommand(USER_ID));

        // then -> 근거는 status에 남고 "지금 막혀 있나"는 Redis가 답한다
        assertThat(leftPlayer().getStatus())
                .isEqualTo(RunningPlayerStatus.MATCHED_LEFT_PENALTY);
        // 기간은 호출자가 넘긴다 — 조기 종료 제재(running-finish.cooldown)와 따로 조절한다
        verify(matchCooldownPort).start(new UserId(USER_ID), COOLDOWN);
    }

    @Test
    @DisplayName("혼자 남은 방에서는 마감이 지났어도 제재하지 않는다")
    void leavingAloneIsExempt() {
        // given -> 1인으로 확정된 방. 혼자 뛰기를 강제하지 않기 위한 출구다(feature-spec)
        givenActiveMatch(room(startAfter(Duration.ofMinutes(5)), 1));

        // when
        cancelMatchHandler.handle(new CancelMatchCommand(USER_ID));

        // then
        assertThat(leftPlayer().getStatus())
                .isEqualTo(RunningPlayerStatus.MATCHED_LEFT_NO_PENALTY);
        verifyNoInteractions(matchCooldownPort);
    }

    @Test
    @DisplayName("마감 정각부터 제재 구간이다")
    void penaltyStartsExactlyAtCloseTime() {
        // given -> 그 시점에 확정 판정이 돈다. 유예는 두지 않는다(feature-spec)
        givenActiveMatch(room(LocalDateTime.now().plus(CLOSE_OFFSET), 3));

        // when
        cancelMatchHandler.handle(new CancelMatchCommand(USER_ID));

        // then
        assertThat(leftPlayer().getStatus())
                .isEqualTo(RunningPlayerStatus.MATCHED_LEFT_PENALTY);
    }

    @Test
    @DisplayName("솔로 방에서 나가는 것은 제재하지 않는다")
    void leavingSoloRoomIsExempt() {
        // given -> 확정 후 방 이탈 제재는 MATCH에만 적용한다(feature-spec)
        givenActiveMatch(soloRoom(startAfter(Duration.ofMinutes(5))));

        // when
        cancelMatchHandler.handle(new CancelMatchCommand(USER_ID));

        // then
        assertThat(leftPlayer().getStatus())
                .isEqualTo(RunningPlayerStatus.MATCHED_LEFT_NO_PENALTY);
        verifyNoInteractions(matchCooldownPort);
    }

    @Test
    @DisplayName("마지막 참가자가 나가면 방이 취소된다")
    void lastPlayerLeavingCancelsRoom() {
        // given -> 참가자가 0이 된 방은 후보 스캔에 계속 걸리므로 닫아야 한다(feature-spec)
        givenActiveMatch(room(startAfter(Duration.ofHours(2)), 1));

        // when
        cancelMatchHandler.handle(new CancelMatchCommand(USER_ID));

        // then
        RunningRoom updated = updatedRoom();
        assertThat(updated.getStatus()).isEqualTo(RunningRoomStatus.CANCELLED);
        assertThat(updated.getPlayerCount().current()).isZero();
        assertThat(updated.getCloseAt()).isPresent();
    }

    @Test
    @DisplayName("남은 사람이 있으면 방은 유지된다")
    void roomSurvivesWhenOthersRemain() {
        // given -> 혼자 남아도 방은 취소하지 않는다(api-spec 5-A)
        givenActiveMatch(room(startAfter(Duration.ofHours(2)), 2));

        // when
        cancelMatchHandler.handle(new CancelMatchCommand(USER_ID));

        // then
        RunningRoom updated = updatedRoom();
        assertThat(updated.getStatus()).isEqualTo(RunningRoomStatus.MATCHING);
        assertThat(updated.getPlayerCount().current()).isOne();
    }

    @Test
    @DisplayName("나가면 세션이 끊기고 이탈 횟수가 오른다")
    void leavingMarksSession() {
        // given
        givenActiveMatch(room(startAfter(Duration.ofHours(2)), 2));

        // when
        cancelMatchHandler.handle(new CancelMatchCommand(USER_ID));

        // then -> 행은 남기고 이력만 새긴다. 다시 배정되면 이 행을 되살린다
        var session = updatedRoom().getSessions().stream()
                .filter(it -> it.isSameUser(new UserId(USER_ID)))
                .findFirst()
                .orElseThrow();
        assertThat(session.isConnected()).isFalse();
        assertThat(session.getLeaveCount().value()).isEqualTo(1);
    }

    @Test
    @DisplayName("러닝이 시작된 뒤에는 취소할 수 없다")
    void cannotCancelAfterRunningStarted() {
        // given -> 여기서 끊으면 WS 종료 경로를 건너뛰어 GPS 트랙과 기록이 저장되지 않는다.
        // 시작이 먼저 커밋된 경합도 여기로 온다 — 잠그고 읽으니 RUNNING이 보여 취소가 막힌다
        given(lockMatchApplicationPort.lockActive(new UserId(USER_ID)))
                .willReturn(Optional.of(player(RunningPlayerStatus.RUNNING)));

        // when & then
        assertThatThrownBy(() -> cancelMatchHandler.handle(new CancelMatchCommand(USER_ID)))
                .isInstanceOf(MatchAlreadyStartedException.class);
        verifyNoInteractions(loadMatchRoomPort, lockMatchRoomPort,
                updateMatchApplicationPort, updateMatchRoomPort, matchCooldownPort);
    }

    @Test
    @DisplayName("활성 신청이 없으면 취소할 것도 없다")
    void rejectsWhenNoActiveApplication() {
        // given
        given(lockMatchApplicationPort.lockActive(new UserId(USER_ID)))
                .willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> cancelMatchHandler.handle(new CancelMatchCommand(USER_ID)))
                .isInstanceOf(ActiveMatchNotFoundException.class);
        verifyNoInteractions(loadMatchRoomPort, lockMatchRoomPort);
    }

    private void givenActiveMatch(RunningRoom room) {
        given(lockMatchApplicationPort.lockActive(new UserId(USER_ID)))
                .willReturn(Optional.of(player(RunningPlayerStatus.JOINED)));
        given(loadMatchRoomPort.findAssignedRoom(new UserId(USER_ID)))
                .willReturn(Optional.of(new RunningRoomId(ROOM_ID)));
        given(lockMatchRoomPort.lockById(new RunningRoomId(ROOM_ID))).willReturn(Optional.of(room));
        // 남은 사람이 있으면 이벤트가 나간다 — 조립 결과는 이 테스트의 주제가 아니라 값만 채워둔다.
        // 방이 비어 발행하지 않는 케이스도 있어 lenient다
        lenient().when(roomInfoAssembler.assemble(any(RunningRoom.class))).thenReturn(ROOM_INFO);
    }

    private RunningPlayer leftPlayer() {
        ArgumentCaptor<RunningPlayer> captor = ArgumentCaptor.forClass(RunningPlayer.class);
        verify(updateMatchApplicationPort).update(captor.capture());
        return captor.getValue();
    }

    private RunningRoom updatedRoom() {
        ArgumentCaptor<RunningRoom> captor = ArgumentCaptor.forClass(RunningRoom.class);
        verify(updateMatchRoomPort).update(captor.capture());
        return captor.getValue();
    }

    private static LocalDateTime startAfter(Duration untilStart) {
        return LocalDateTime.now().plus(untilStart);
    }

    private static RunningPlayer player(RunningPlayerStatus status) {
        return RunningPlayer.builder()
                .runningPlayerId(PLAYER_ID)
                .userId(USER_ID)
                .status(status)
                .avgPace(AVG_PACE)
                .targetDistance(TARGET_DISTANCE)
                .startAt(LocalDateTime.now().plusHours(2))
                .build();
    }

    private static RunningRoom room(LocalDateTime startAt, int currentPlayerCount) {
        return matchRoom(RunningRoomType.MATCH, startAt, currentPlayerCount, 4);
    }

    private static RunningRoom soloRoom(LocalDateTime startAt) {
        return matchRoom(RunningRoomType.SOLO, startAt, 1, 1);
    }

    // 나 말고 나머지 인원은 다른 유저의 세션으로 채운다 — 세션 키가 유저다
    private static RunningRoom matchRoom(RunningRoomType type, LocalDateTime startAt,
                                         int currentPlayerCount, int maxPlayerCount) {
        List<SessionDraft> sessions = new ArrayList<>();
        sessions.add(new SessionDraft(
                new UserId(USER_ID), new RunningPlayerId(PLAYER_ID), 0, true));
        for (int i = 1; i < currentPlayerCount; i++) {
            sessions.add(new SessionDraft(
                    new UserId(UuidCreator.getTimeOrderedEpoch()),
                    new RunningPlayerId(PLAYER_ID + i), 0, true));
        }
        return RunningRoom.builder()
                .runningRoomId(ROOM_ID)
                .type(type)
                .status(RunningRoomStatus.MATCHING)
                .startAt(startAt)
                .targetDistance(TARGET_DISTANCE)
                .avgPace(AVG_PACE)
                .currentPlayerCount(currentPlayerCount)
                .maxPlayerCount(maxPlayerCount)
                .sessions(sessions)
                .build();
    }

    @Test
    @DisplayName("남은 참가자에게 방 갱신을 발행한다")
    void publishesRoomUpdateToRemainingPlayers() {
        // given -> 나가면 남은 사람 화면의 인원이 줄어야 한다
        givenActiveMatch(room(startAfter(Duration.ofHours(2)), 2));
        given(roomInfoAssembler.assemble(any(RunningRoom.class))).willReturn(ROOM_INFO);

        // when
        cancelMatchHandler.handle(new CancelMatchCommand(USER_ID));

        // then -> 커밋 후에 나가도록 스프링 이벤트로 넘긴다
        verify(eventPublisher).publishEvent(new MatchRoomChangedEvent(
                MatchStreamEvent.updated(ROOM_INFO)));
    }

    @Test
    @DisplayName("방이 비면 발행하지 않는다")
    void skipsPublishWhenRoomIsEmpty() {
        // given -> 마지막 참가자가 나가 인원이 0이 된다. 받을 사람이 없다
        givenActiveMatch(room(startAfter(Duration.ofHours(2)), 1));

        // when
        cancelMatchHandler.handle(new CancelMatchCommand(USER_ID));

        // then
        verifyNoInteractions(eventPublisher, roomInfoAssembler);
    }
}
