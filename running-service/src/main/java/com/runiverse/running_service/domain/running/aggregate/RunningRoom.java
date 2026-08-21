package com.runiverse.running_service.domain.running.aggregate;

import com.runiverse.running_service.domain.running.exception.AlreadyRoomPlayerException;
import com.runiverse.running_service.domain.running.exception.InvalidCloseAtException;
import com.runiverse.running_service.domain.running.exception.NotRoomPlayerException;
import com.runiverse.running_service.domain.running.exception.PlayerAlreadyLeftException;
import com.runiverse.running_service.domain.running.exception.RoomNotJoinableException;
import com.runiverse.running_service.domain.running.exception.RunningRoomTypeRequiredException;
import com.runiverse.running_service.domain.running.exception.StartAtRequiredException;
import com.runiverse.running_service.domain.running.vo.Distance;
import com.runiverse.running_service.domain.running.vo.Pace;
import com.runiverse.running_service.domain.running.vo.PlayerCount;
import com.runiverse.running_service.domain.running.vo.RunningPlayerId;
import com.runiverse.running_service.domain.running.vo.RunningRoomId;
import com.runiverse.running_service.domain.running.vo.RunningRoomStatus;
import com.runiverse.running_service.domain.running.vo.RunningRoomType;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Getter
public class RunningRoom {

    private static final int SOLO_MAX_PLAYER = 1;
    private static final int MATCH_MAX_PLAYER = 4;
    private final RunningRoomId runningRoomId;   // 저장 전에는 null
    private final RunningRoomType type;          // 생성 후 바뀌지 않는다
    private final LocalDateTime startAt;
    private final LocalDateTime closeAt;         // 솔로는 null
    private final Distance targetDistance;       // 정해진 뒤 바뀌지 않는다
    private RunningRoomStatus status;
    private PlayerCount playerCount;
    private Pace avgPace;
    private final List<RoomSession> sessions;    // 방이 맺고 있는 관계들

    @Builder
    private RunningRoom(Long runningRoomId, RunningRoomType type, RunningRoomStatus status,
                        LocalDateTime startAt, LocalDateTime closeAt,
                        Integer targetDistance, int avgPace,
                        int currentPlayerCount, int maxPlayerCount,
                        List<RoomSession> sessions) {
        this.runningRoomId = runningRoomId == null ? null : new RunningRoomId(runningRoomId);
        if (type == null) {
            throw new RunningRoomTypeRequiredException();
        }
        this.type = type;
        this.status = status == null ? type.initialStatus() : status;
        if (startAt == null) {
            throw new StartAtRequiredException();
        }
        this.startAt = startAt;
        validateCloseAt(type, startAt, closeAt);
        this.closeAt = closeAt;
        this.targetDistance = targetDistance == null ? null : new Distance(targetDistance);
        this.avgPace = new Pace(avgPace);
        this.playerCount = new PlayerCount(currentPlayerCount, maxPlayerCount);
        this.sessions = sessions == null ? new ArrayList<>() : new ArrayList<>(sessions);
    }


    // 솔로 — 모집 단계 없이 STARTED로 태어난다
    public static RunningRoom openSolo(Long runningPlayerId, int avgPace,
                                       Integer targetDistance, LocalDateTime startAt) {
        RunningRoom room = builder()
                .type(RunningRoomType.SOLO)
                .startAt(startAt)
                .targetDistance(targetDistance)
                .avgPace(avgPace)
                .currentPlayerCount(1)
                .maxPlayerCount(SOLO_MAX_PLAYER)
                .build();
        room.sessions.add(RoomSession.open(runningPlayerId));
        return room;
    }

    // 매칭 — 항상 1인 방으로 태어나 close_at까지 모집한다
    public static RunningRoom openMatch(Long runningPlayerId, int avgPace, Integer targetDistance,
                                        LocalDateTime startAt, LocalDateTime closeAt) {
        RunningRoom room = builder()
                .type(RunningRoomType.MATCH)
                .startAt(startAt)
                .closeAt(closeAt)
                .targetDistance(targetDistance)
                .avgPace(avgPace)
                .currentPlayerCount(1)
                .maxPlayerCount(MATCH_MAX_PLAYER)
                .build();
        room.sessions.add(RoomSession.open(runningPlayerId));
        return room;
    }

    public void start() {
        this.status = status.transitionTo(RunningRoomStatus.STARTED);
    }

    public void finish() {
        this.status = status.transitionTo(RunningRoomStatus.FINISHED);
    }

    public void cancel() {
        this.status = status.transitionTo(RunningRoomStatus.CANCELLED);
    }

    // 참가자 페이스는 다른 애그리거트라 application이 읽어 넘긴다
    public void recalculateAvgPace(List<Pace> paces) {
        if (paces.isEmpty()) {
            return;
        }
        int sum = paces.stream().mapToInt(Pace::secondsPerKm).sum();
        this.avgPace = new Pace(sum / paces.size());
    }

    // 후보 스캔이 걸러도 애그리거트가 다시 지킨다 — 스캔과 합류 사이에 자리가 찰 수 있다
    public void join(Long runningPlayerId, Pace pace) {
        if (!canJoin(pace)) {
            throw new RoomNotJoinableException();
        }
        if (findSession(runningPlayerId).isPresent()) {
            throw new AlreadyRoomPlayerException();   // 한 플레이어 = 최대 한 방
        }
        this.playerCount = playerCount.join();
        this.sessions.add(RoomSession.open(runningPlayerId));
    }

    // start_at·target_distance는 조회가 등가로 거르고, 페이스 근접만 방이 판정한다
    // 지금 pace 조건이 있기는 한데 나중에 수정 가능성 있음
    public boolean canJoin(Pace pace) {
        return status == RunningRoomStatus.MATCHING
                && playerCount.canJoin()
                && avgPace.isCloseTo(pace);
    }

    // close_at 도달 — 인원 수와 무관하게 확정된다(1인이면 1인으로 확정돼 혼자 뛴다)
    public void closeMatching() {
        this.status = status.transitionTo(RunningRoomStatus.MATCHED);
    }

    // 나갔던 사람이 돌아옴 — 러닝 중에도 가능해서 join()의 모집 조건을 타지 않는다
    public void rejoin(Long runningPlayerId) {
        if (status.isTerminal()) {
            throw new RoomNotJoinableException();   // 끝났거나 취소된 방엔 돌아올 수 없다
        }
        RoomSession session = session(runningPlayerId);
        if (session.isConnected()) {
            throw new AlreadyRoomPlayerException();
        }
        this.playerCount = playerCount.join();   // 그새 자리가 찼으면 RoomIsFullException
        session.rejoin();
    }

    public void leave(Long runningPlayerId) {
        RoomSession session = session(runningPlayerId);   // 참가자 확인이 먼저 — 아니면 인원만 줄고 예외가 난다
        if (!session.isConnected()) {
            // 인원을 줄이기 전에 막는다 — 뒤로 가면 예외가 나도 playerCount는 이미 줄어 있다
            throw new PlayerAlreadyLeftException();
        }
        this.playerCount = playerCount.leave();
        session.leave();
        if (playerCount.current() == 0 && status.isBeforeStart()) {
            cancel();   // 시작 전 빈 방만 닫는다 — 시작 후엔 기록이 남아야 해서 FINISHED로 간다
        }
    }

    public boolean isNew() {
        return runningRoomId == null;
    }

    public Optional<RunningRoomId> getRunningRoomId() {
        return Optional.ofNullable(runningRoomId);
    }

    public Optional<Distance> getTargetDistance() {
        return Optional.ofNullable(targetDistance);
    }

    public Optional<LocalDateTime> getCloseAt() {
        return Optional.ofNullable(closeAt);
    }

    public List<RoomSession> getSessions() {
        return Collections.unmodifiableList(sessions);
    }

    private RoomSession session(Long runningPlayerId) {
        return findSession(runningPlayerId).orElseThrow(NotRoomPlayerException::new);
    }

    private Optional<RoomSession> findSession(Long runningPlayerId) {
        RunningPlayerId playerId = new RunningPlayerId(runningPlayerId);
        return sessions.stream().filter(s -> s.isSamePlayer(playerId)).findFirst();
    }

    private static void validateCloseAt(RunningRoomType type, LocalDateTime startAt,
                                        LocalDateTime closeAt) {
        if (type == RunningRoomType.SOLO) {
            if (closeAt != null) {
                throw new InvalidCloseAtException();   // 솔로는 모집 단계가 없다
            }
            return;
        }
        if (closeAt == null || !closeAt.isBefore(startAt)) {
            throw new InvalidCloseAtException();
        }
    }
}
