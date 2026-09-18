package com.runiverse.running_service.unit_test.match.application;

import com.github.f4b6a3.uuid.UuidCreator;
import com.runiverse.running_service.application.match.command.apply.MatchRoomAssigner;
import com.runiverse.running_service.application.common.port.out.ScheduleJobPort;
import com.runiverse.running_service.application.match.common.MatchProperties;
import com.runiverse.running_service.application.match.port.out.CreateMatchRoomPort;
import com.runiverse.running_service.application.match.port.out.LoadMatchCandidatesPort;
import com.runiverse.running_service.application.match.port.out.LoadMatchPlayersPort;
import com.runiverse.running_service.application.match.port.out.LockMatchRoomPort;
import com.runiverse.running_service.application.match.port.out.MatchCandidate;
import com.runiverse.running_service.application.match.port.out.MatchPlayer;
import com.runiverse.running_service.application.match.port.out.UpdateMatchRoomPort;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.scheduling.vo.ScheduledJobType;
import com.runiverse.running_service.domain.running.metric.vo.Pace;
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

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@DisplayName("매칭 방 배정 단위 테스트")
class MatchRoomAssignerTest {

    private static final UserId APPLICANT = new UserId(UuidCreator.getTimeOrderedEpoch());
    private static final RunningPlayerId APPLICATION = new RunningPlayerId(7L);
    private static final Pace MY_PACE = new Pace(360);            // 6분/km
    private static final LocalDateTime START_AT = LocalDateTime.of(2026, 7, 25, 19, 0);
    private static final int TARGET_DISTANCE = 5_000;
    private static final Duration CLOSE_OFFSET = Duration.ofMinutes(15);
    private static final Duration READY_OFFSET = Duration.ofSeconds(10);
    private static final Duration FORCE_FINISH_OFFSET = Duration.ofHours(6);
    // 페이스 차를 이 단위로 끊어 같은 구간이면 내 이탈 이력이 순위를 가른다
    private static final int PACE_TIE_TOLERANCE = 10;
    // 이 테스트가 다루는 흐름은 아니지만 프로퍼티가 요구한다
    private static final Duration COOLDOWN = Duration.ofMinutes(20);
    private static final long NEW_ROOM_ID = 999L;

    @Mock
    private LoadMatchCandidatesPort loadMatchCandidatesPort;

    @Mock
    private LockMatchRoomPort lockMatchRoomPort;

    @Mock
    private LoadMatchPlayersPort loadMatchPlayersPort;

    @Mock
    private UpdateMatchRoomPort updateMatchRoomPort;

    @Mock
    private CreateMatchRoomPort createMatchRoomPort;

    @Mock
    private ScheduleJobPort scheduleJobPort;

    private MatchRoomAssigner matchRoomAssigner;

    @BeforeEach
    void setUp() {
        matchRoomAssigner = new MatchRoomAssigner(
                loadMatchCandidatesPort, lockMatchRoomPort, loadMatchPlayersPort,
                updateMatchRoomPort, createMatchRoomPort, scheduleJobPort,
                new MatchProperties(CLOSE_OFFSET, READY_OFFSET, FORCE_FINISH_OFFSET,
                        PACE_TIE_TOLERANCE, COOLDOWN));
    }

    @Test
    @DisplayName("후보가 없으면 1인 방을 새로 연다")
    void opensNewRoomWhenNoCandidate() {
        // given -> "방 미배정" 상태는 없다 — 붙을 방이 없으면 만들어서라도 배정한다(feature-spec)
        givenCandidates();
        given(createMatchRoomPort.create(any())).willReturn(savedRoom(NEW_ROOM_ID));

        // when
        RunningRoomId assigned = assign();

        // then
        assertThat(assigned.value()).isEqualTo(NEW_ROOM_ID);
        verifyNoInteractions(lockMatchRoomPort, updateMatchRoomPort);
    }

    @Test
    @DisplayName("새 방은 신청자의 페이스·거리·슬롯을 그대로 갖는다")
    void newRoomInheritsApplicantCondition() {
        // given
        givenCandidates();
        given(createMatchRoomPort.create(any())).willReturn(savedRoom(NEW_ROOM_ID));

        // when
        assign();

        // then -> 1인 방은 창설자 페이스가 곧 방 평균이다
        ArgumentCaptor<RunningRoom> captor = ArgumentCaptor.forClass(RunningRoom.class);
        verify(createMatchRoomPort).create(captor.capture());
        RunningRoom created = captor.getValue();
        assertThat(created.getType()).isEqualTo(RunningRoomType.MATCH);
        assertThat(created.getStatus()).isEqualTo(RunningRoomStatus.MATCHING);
        assertThat(created.getStartAt()).isEqualTo(START_AT);
        assertThat(created.getAvgPace()).map(Pace::secondsPerKm).contains(MY_PACE.secondsPerKm());
        assertThat(created.getPlayerCount().current()).isOne();
        assertThat(created.getPlayerCount().max()).isEqualTo(4);
    }

    @Test
    @DisplayName("페이스가 아무리 멀어도 후보가 하나뿐이면 그 방에 붙인다")
    void joinsFarPaceRoomWhenItIsTheOnlyCandidate() {
        // given -> 6분/km인 신청자와 7분 33초/km인 방. 옛 ±30초 자격이면 걸러졌을 조합이다
        givenCandidates(candidate(1L, MY_PACE.secondsPerKm() + 93, 0));
        givenJoinable(1L);

        // when
        RunningRoomId assigned = assign();

        // then -> 혼자 뛰게 두느니 붙인다. 새 방은 열지 않는다
        assertThat(assigned.value()).isEqualTo(1L);
        verifyNoInteractions(createMatchRoomPort);
    }

    @Test
    @DisplayName("페이스가 가장 가까운 방을 고른다")
    void picksClosestPace() {
        // given -> 차이가 25·3·14초다. 10초 단위로 끊으면 2·0·1번 구간이다. 순서는 뒤섞어 둔다
        givenCandidates(
                candidate(1L, MY_PACE.secondsPerKm() + 25, 0),
                candidate(2L, MY_PACE.secondsPerKm() + 3, 0),
                candidate(3L, MY_PACE.secondsPerKm() - 14, 0));
        givenJoinable(2L);

        // when
        RunningRoomId assigned = assign();

        // then
        assertThat(assigned.value()).isEqualTo(2L);
    }

    @Test
    @DisplayName("페이스가 비슷하면 내가 덜 나갔던 방을 고른다")
    void breaksPaceTieByMyLeaveCount() {
        // given -> 차이가 3초와 9초로 같은 10초 구간이라 동급이다.
        //          그러면 내가 등지고 나온 적이 적은 방이 이긴다(feature-spec 방 배정 기준)
        givenCandidates(
                candidate(1L, MY_PACE.secondsPerKm() + 3, 5),
                candidate(2L, MY_PACE.secondsPerKm() + 9, 1));
        givenJoinable(2L);

        // when
        RunningRoomId assigned = assign();

        // then -> 페이스만 보면 1번이 가깝지만 동급 구간이라 내 이탈 이력이 갈랐다
        assertThat(assigned.value()).isEqualTo(2L);
    }

    @Test
    @DisplayName("페이스 구간이 갈리면 내 이탈 이력보다 페이스가 우선한다")
    void paceWinsOverMyLeaveCountBeyondTolerance() {
        // given -> 2초와 20초는 10초 경계를 사이에 두고 구간이 갈린다
        givenCandidates(
                candidate(1L, MY_PACE.secondsPerKm() + 2, 9),
                candidate(2L, MY_PACE.secondsPerKm() + 20, 0));
        givenJoinable(1L);

        // when
        RunningRoomId assigned = assign();

        // then -> 아홉 번 나온 방이라도 페이스가 가까우면 거기로 간다
        assertThat(assigned.value()).isEqualTo(1L);
    }

    @Test
    @DisplayName("내가 나왔던 방이라도 후보가 그것뿐이면 다시 들어간다")
    void joinsPreviouslyLeftRoomWhenItIsTheOnlyCandidate() {
        // given -> 이탈 이력은 순위만 낮출 뿐 문을 잠그지 않는다(erd running_room_sessions)
        givenCandidates(candidate(1L, MY_PACE.secondsPerKm(), 3));
        givenJoinable(1L);

        // when
        RunningRoomId assigned = assign();

        // then
        assertThat(assigned.value()).isEqualTo(1L);
        verifyNoInteractions(createMatchRoomPort);
    }

    @Test
    @DisplayName("페이스도 이탈 이력도 같으면 오래된 방부터 채운다")
    void breaksFullTieByRoomId() {
        // given -> 셋 다 거쳐 간 적 없는 방이라 이탈 이력이 전부 0이다.
        //          방 번호로 갈라 두지 않으면 DB 반환 순서라 배정이 비결정적이다
        givenCandidates(
                candidate(5L, MY_PACE.secondsPerKm() + 1, 0),
                candidate(2L, MY_PACE.secondsPerKm() + 4, 0),
                candidate(9L, MY_PACE.secondsPerKm() + 7, 0));
        givenJoinable(2L);

        // when
        RunningRoomId assigned = assign();

        // then
        assertThat(assigned.value()).isEqualTo(2L);
    }

    @Test
    @DisplayName("그새 자리가 찬 방은 건너뛰고 다음 후보로 간다")
    void fallsBackToNextCandidateWhenRoomFilledUp() {
        // given -> 스캔은 잠금 없이 했다. 1번은 그 사이 정원이 찼다
        givenCandidates(
                candidate(1L, MY_PACE.secondsPerKm(), 0),
                candidate(2L, MY_PACE.secondsPerKm() + 5, 0));
        given(lockMatchRoomPort.lockById(new RunningRoomId(1L)))
                .willReturn(Optional.of(room(1L, MY_PACE.secondsPerKm(), 4)));
        givenJoinable(2L);

        // when
        RunningRoomId assigned = assign();

        // then
        assertThat(assigned.value()).isEqualTo(2L);
        verifyNoInteractions(createMatchRoomPort);
    }

    @Test
    @DisplayName("후보가 전부 막히면 결국 새 방을 연다")
    void opensNewRoomWhenEveryCandidateRejects() {
        // given -> 잠근 사이 마감돼 모집 상태가 아니게 된 방
        givenCandidates(candidate(1L, MY_PACE.secondsPerKm(), 0));
        given(lockMatchRoomPort.lockById(new RunningRoomId(1L)))
                .willReturn(Optional.of(closedRoom(1L)));
        given(createMatchRoomPort.create(any())).willReturn(savedRoom(NEW_ROOM_ID));

        // when
        RunningRoomId assigned = assign();

        // then
        assertThat(assigned.value()).isEqualTo(NEW_ROOM_ID);
        verifyNoInteractions(updateMatchRoomPort);
    }

    @Test
    @DisplayName("합류하면 기존 참가자와 신청자를 합쳐 방 평균 페이스를 다시 계산한다")
    void recalculatesRoomAveragePaceAfterJoin() {
        // given -> 기존 참가자 380·340, 신청자 360 → 평균 360
        givenCandidates(candidate(1L, 360, 0));
        given(lockMatchRoomPort.lockById(new RunningRoomId(1L)))
                .willReturn(Optional.of(room(1L, 360, 2)));
        given(loadMatchPlayersPort.loadPlayers(new RunningRoomId(1L))).willReturn(List.of(
                new MatchPlayer(UuidCreator.getTimeOrderedEpoch(), 380),
                new MatchPlayer(UuidCreator.getTimeOrderedEpoch(), 340)));

        // when
        assign();

        // then -> 합류자의 세션은 아직 저장 전이라 조회에 안 잡힌다. 빠뜨리면 평균이 틀어진다
        ArgumentCaptor<RunningRoom> captor = ArgumentCaptor.forClass(RunningRoom.class);
        verify(updateMatchRoomPort).update(captor.capture());
        RunningRoom joined = captor.getValue();
        assertThat(joined.getAvgPace()).map(Pace::secondsPerKm).contains(360);
        assertThat(joined.getPlayerCount().current()).isEqualTo(3);
    }

    @Test
    @DisplayName("전에 나갔던 방에 다시 배정되면 세션을 새로 만들지 않고 되살린다")
    void revivesSessionWhenReassignedToPreviousRoom() {
        // given -> 취소해서 is_connected=false로 남아 있던 세션. 키가 유저라 행이 하나다(erd)
        givenCandidates(candidate(1L, MY_PACE.secondsPerKm(), 1));
        given(lockMatchRoomPort.lockById(new RunningRoomId(1L)))
                .willReturn(Optional.of(roomWithLeftSession(1L)));
        given(loadMatchPlayersPort.loadPlayers(new RunningRoomId(1L))).willReturn(List.of());

        // when
        assign();

        // then -> 이탈 이력(leave_count)은 그대로 남고 신청만 새것으로 갈린다
        ArgumentCaptor<RunningRoom> captor = ArgumentCaptor.forClass(RunningRoom.class);
        verify(updateMatchRoomPort).update(captor.capture());
        assertThat(captor.getValue().getSessions()).hasSize(1);
        var session = captor.getValue().getSessions().getFirst();
        assertThat(session.isSameUser(APPLICANT)).isTrue();
        assertThat(session.isConnected()).isTrue();
        assertThat(session.getRunningPlayerId()).isEqualTo(APPLICATION);
        assertThat(session.getLeaveCount().value()).isEqualTo(1);
    }

    // 배정 결과를 RoomInfo로 조립해 스트림에 실어야 해서 방 객체를 돌려준다
    private RunningRoomId assign() {
        return matchRoomAssigner.assign(
                        APPLICANT, APPLICATION, MY_PACE, START_AT, TARGET_DISTANCE)
                .getRunningRoomId()
                .orElseThrow();
    }

    private void givenCandidates(MatchCandidate... candidates) {
        given(loadMatchCandidatesPort.loadCandidates(APPLICANT, START_AT, TARGET_DISTANCE))
                .willReturn(List.of(candidates));
    }

    // 자리가 남은 방을 잠금 조회에 물려 둔다 — 참가자 조회는 평균 재계산에서만 쓴다
    private void givenJoinable(long roomId) {
        given(lockMatchRoomPort.lockById(new RunningRoomId(roomId)))
                .willReturn(Optional.of(room(roomId, MY_PACE.secondsPerKm(), 1)));
        given(loadMatchPlayersPort.loadPlayers(new RunningRoomId(roomId))).willReturn(List.of());
    }

    // myLeaveCount는 방 전체 합이 아니라 신청자가 그 방을 나간 횟수다
    private static MatchCandidate candidate(long roomId, int avgPace, int myLeaveCount) {
        return new MatchCandidate(roomId, avgPace, myLeaveCount);
    }

    // 모집 중인 방 — 세션은 이미 있는 다른 참가자의 것이다
    private static RunningRoom room(long roomId, int avgPace, int currentPlayerCount) {
        return RunningRoom.builder()
                .runningRoomId(roomId)
                .type(RunningRoomType.MATCH)
                .status(RunningRoomStatus.MATCHING)
                .startAt(START_AT)
                .targetDistance(TARGET_DISTANCE)
                .avgPace(avgPace)
                .currentPlayerCount(currentPlayerCount)
                .maxPlayerCount(4)
                .sessions(List.of(new SessionDraft(
                        new UserId(UuidCreator.getTimeOrderedEpoch()),
                        new RunningPlayerId(1L), 0, true)))
                .build();
    }

    // 신청자가 전에 이 방에 있다 나간 상태 — 인원에는 안 잡히고 세션만 남아 있다
    private static RunningRoom roomWithLeftSession(long roomId) {
        return RunningRoom.builder()
                .runningRoomId(roomId)
                .type(RunningRoomType.MATCH)
                .status(RunningRoomStatus.MATCHING)
                .startAt(START_AT)
                .targetDistance(TARGET_DISTANCE)
                .avgPace(MY_PACE.secondsPerKm())
                .currentPlayerCount(0)
                .maxPlayerCount(4)
                .sessions(List.of(new SessionDraft(
                        APPLICANT, new RunningPlayerId(3L), 1, false)))
                .build();
    }

    // 잠근 사이 마감돼 더는 모집하지 않는 방
    private static RunningRoom closedRoom(long roomId) {
        return RunningRoom.builder()
                .runningRoomId(roomId)
                .type(RunningRoomType.MATCH)
                .status(RunningRoomStatus.MATCHED)
                .startAt(START_AT)
                .targetDistance(TARGET_DISTANCE)
                .avgPace(MY_PACE.secondsPerKm())
                .currentPlayerCount(1)
                .maxPlayerCount(4)
                .sessions(List.of(new SessionDraft(
                        new UserId(UuidCreator.getTimeOrderedEpoch()),
                        new RunningPlayerId(1L), 0, true)))
                .build();
    }

    private static RunningRoom savedRoom(long roomId) {
        return RunningRoom.builder()
                .runningRoomId(roomId)
                .type(RunningRoomType.MATCH)
                .status(RunningRoomStatus.MATCHING)
                .startAt(START_AT)
                .targetDistance(TARGET_DISTANCE)
                .avgPace(MY_PACE.secondsPerKm())
                .currentPlayerCount(1)
                .maxPlayerCount(4)
                .sessions(List.of(new SessionDraft(
                        APPLICANT, APPLICATION, 0, true)))
                .build();
    }

    @Test
    @DisplayName("방을 새로 열면 마감 확정을 예약한다")
    void schedulesCloseForNewRoom() {
        // given -> 이 예약이 없으면 방이 영원히 MATCHING에 머문다
        givenCandidates();
        given(createMatchRoomPort.create(any())).willReturn(savedRoom(NEW_ROOM_ID));

        // when
        assign();

        // then -> 마감은 start_at - 오프셋이다. 컬럼에 저장하지 않고 여기서 계산한다
        verify(scheduleJobPort).schedule(
                ScheduledJobType.MATCH_CLOSE, NEW_ROOM_ID, START_AT.minus(CLOSE_OFFSET));
    }

    @Test
    @DisplayName("방을 새로 열면 시작 통지도 함께 예약한다")
    void schedulesRunningReadyForNewRoom() {
        // given -> 이 예약이 없으면 클라가 발사 기준을 못 받아 기기 시각에만 의존한다
        givenCandidates();
        given(createMatchRoomPort.create(any())).willReturn(savedRoom(NEW_ROOM_ID));

        // when
        assign();

        // then -> 정각이 아니라 리드타임만큼 앞이다. 정각에 보내면 클라 발사와 겹쳐 늘 늦는다
        verify(scheduleJobPort).schedule(
                ScheduledJobType.RUNNING_READY, NEW_ROOM_ID, START_AT.minus(READY_OFFSET));
    }

    @Test
    @DisplayName("방을 새로 열면 정각 시작도 함께 예약한다")
    void schedulesRunningStartForNewRoom() {
        // given -> 이 예약이 없으면 아무도 채널에 붙지 않은 방이 확정 상태에 갇힌다
        givenCandidates();
        given(createMatchRoomPort.create(any())).willReturn(savedRoom(NEW_ROOM_ID));

        // when
        assign();

        // then -> 오프셋 없는 start_at 정각이다. 시작 통지와 달리 앞당기지 않는다
        verify(scheduleJobPort).schedule(
                ScheduledJobType.RUNNING_START, NEW_ROOM_ID, START_AT);
    }

    @Test
    @DisplayName("방을 새로 열면 강제 종료도 함께 예약한다")
    void schedulesForceFinishForNewRoom() {
        // given -> 이 예약이 없으면 종료 메시지가 오지 않은 방이 영영 열린 채로 남는다
        givenCandidates();
        given(createMatchRoomPort.create(any())).willReturn(savedRoom(NEW_ROOM_ID));

        // when
        assign();

        // then -> 마감·통지와 달리 유일하게 start_at 뒤다
        verify(scheduleJobPort).schedule(
                ScheduledJobType.RUNNING_FORCE_FINISH, NEW_ROOM_ID,
                START_AT.plus(FORCE_FINISH_OFFSET));
    }

    @Test
    @DisplayName("기존 방에 합류할 때는 예약하지 않는다")
    void doesNotScheduleWhenJoiningExistingRoom() {
        // given -> 그 방을 연 신청자가 이미 걸어뒀다. 또 걸면 UNIQUE에 부딪힌다
        givenCandidates(candidate(1L, MY_PACE.secondsPerKm(), 0));
        given(lockMatchRoomPort.lockById(new RunningRoomId(1L)))
                .willReturn(Optional.of(room(1L, MY_PACE.secondsPerKm(), 1)));

        // when
        assign();

        // then
        verifyNoInteractions(scheduleJobPort);
    }
}
