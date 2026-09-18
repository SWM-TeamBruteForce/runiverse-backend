package com.runiverse.running_service.application.match.command.apply;

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
import com.runiverse.running_service.domain.running.metric.vo.Pace;
import com.runiverse.running_service.domain.running.player.vo.RunningPlayerId;
import com.runiverse.running_service.domain.running.room.RunningRoom;
import com.runiverse.running_service.domain.running.room.exception.RoomNotJoinableException;
import com.runiverse.running_service.domain.running.room.vo.RunningRoomId;
import com.runiverse.running_service.domain.scheduling.vo.ScheduledJobType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;


// 후보 스캔·정렬·합류를 맡는다 — 핸들러는 신청 자격과 신청 생성만 본다
@Slf4j
@Component
@RequiredArgsConstructor
public class MatchRoomAssigner {

    private final LoadMatchCandidatesPort loadMatchCandidatesPort;
    private final LockMatchRoomPort lockMatchRoomPort;
    private final LoadMatchPlayersPort loadMatchPlayersPort;
    private final UpdateMatchRoomPort updateMatchRoomPort;
    private final CreateMatchRoomPort createMatchRoomPort;
    private final ScheduleJobPort scheduleJobPort;
    private final MatchProperties matchProperties;

    // 붙을 방이 없으면 1인 방을 새로 연다 — "방 미배정" 상태는 없다(feature-spec).
    // 세션의 키가 유저라 배정에는 둘 다 필요하다 — 유저로 자리를 잡고 신청을 그 자리에 꽂는다
    public RunningRoom assign(UserId userId, RunningPlayerId playerId, Pace pace,
                              LocalDateTime startAt, int targetDistanceMeters) {
        for (MatchCandidate candidate : ranked(userId, pace, startAt, targetDistanceMeters)) {
            RunningRoomId roomId = new RunningRoomId(candidate.runningRoomId());
            // 잠금 없이 스캔했으므로 그새 자리가 찼을 수 있다 — 확정 직전에 잠그고 다시 읽는다
            Optional<RunningRoom> locked = lockMatchRoomPort.lockById(roomId);
            if (locked.isEmpty()) {
                continue;
            }
            RunningRoom room = locked.get();
            try {
                room.join(userId, playerId);
            } catch (RoomNotJoinableException e) {
                // 스캔과 합류 사이에 마감됐거나 자리가 찼다 — 다음 후보로 넘어간다
                log.debug("후보 방 합류 실패 — roomId={}", roomId.value());
                continue;
            }
            room.recalculateAvgPace(pacesAfterJoin(roomId, pace));
            updateMatchRoomPort.update(room);
            return room;
        }
        return openNewRoom(userId, playerId, pace, startAt, targetDistanceMeters);
    }

    // 페이스로도 이탈 이력으로도 거르지 않는다 — 둘 다 순위일 뿐이다(feature-spec 방 배정 기준).
    // ① 내 페이스에 가까운 방 ② 같은 구간이면 내가 덜 나갔던 방 ③ 그래도 같으면 오래된 방.
    // 후보가 하나도 없을 때만 새 방을 연다
    private List<MatchCandidate> ranked(UserId userId, Pace pace, LocalDateTime startAt,
                                        int targetDistanceMeters) {
        int tolerance = matchProperties.paceTieToleranceSecondsPerKm();
        return loadMatchCandidatesPort.loadCandidates(userId, startAt, targetDistanceMeters).stream()
                .sorted(Comparator
                        // 차이를 임계 단위로 뭉뚱그려 같은 구간이면 이탈 이력이 순위를 가르게 한다
                        .comparingInt((MatchCandidate candidate) ->
                                pace.gapTo(paceOf(candidate)) / tolerance)
                        .thenComparingInt(MatchCandidate::myLeaveCount)
                        // 거쳐 간 적 없는 방끼리는 전부 0이라 여기서 갈린다 —
                        // 없으면 DB 반환 순서라 배정이 비결정적이고, 방도 잘게 쪼개진다
                        .thenComparingLong(MatchCandidate::runningRoomId))
                .toList();
    }


    // 합류자의 세션은 아직 저장 전이라 조회에 안 잡힌다 — 기존 참가자에 합류자를 더해 계산한다
    private List<Pace> pacesAfterJoin(RunningRoomId roomId, Pace joined) {
        List<Pace> paces = new ArrayList<>(loadMatchPlayersPort.loadPlayers(roomId).stream()
                .map(MatchRoomAssigner::paceOf)
                .toList());
        paces.add(joined);
        return paces;
    }

    private RunningRoom openNewRoom(UserId userId, RunningPlayerId playerId, Pace pace,
                                    LocalDateTime startAt, int targetDistanceMeters) {
        // 1인 방은 창설자 페이스가 곧 방 평균이라 재계산할 것이 없다
        RunningRoom room = createMatchRoomPort.create(RunningRoom.openMatch(
                userId, playerId, pace.secondsPerKm(), targetDistanceMeters, startAt));
        // 방이 새로 생길 때만 건다 — 기존 방에 합류하면 그 방 예약이 이미 있다
        Long roomId = room.getRunningRoomId().orElseThrow().value();
        scheduleJobPort.schedule(
                ScheduledJobType.MATCH_CLOSE, roomId,
                startAt.minus(matchProperties.closeOffset()));
        // 확정 시점이 아니라 여기서 함께 건다 — 어차피 방이 취소·시작됐는지는 발화 때 다시 봐야 한다
        scheduleJobPort.schedule(
                ScheduledJobType.RUNNING_READY, roomId,
                startAt.minus(matchProperties.readyOffset()));
        // 정각에 방을 올린다 — 아무도 채널에 붙지 않아도 방이 확정 상태에 갇히지 않는다.
        // 참가자가 먼저 도착하면 그쪽이 올리고 이 예약은 이미 STARTED를 보고 빠진다
        scheduleJobPort.schedule(ScheduledJobType.RUNNING_START, roomId, startAt);
        // 여기서 함께 건다 — 6시간 뒤에 방을 훑는 대신 방마다 그 시각에만 깨운다
        scheduleJobPort.schedule(
                ScheduledJobType.RUNNING_FORCE_FINISH, roomId,
                startAt.plus(matchProperties.forceFinishOffset()));
        return room;
    }


    private static Pace paceOf(MatchCandidate candidate) {
        return new Pace(candidate.avgPaceSecondsPerKm());
    }

    private static Pace paceOf(MatchPlayer player) {
        return new Pace(player.avgPaceSecondsPerKm());
    }
}
