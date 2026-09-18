package com.runiverse.running_service.domain.running.room;

import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.running.metric.vo.Distance;
import com.runiverse.running_service.domain.running.metric.vo.Pace;
import com.runiverse.running_service.domain.running.player.vo.RunningPlayerId;
import com.runiverse.running_service.domain.running.room.exception.AlreadyLeftRoomException;
import com.runiverse.running_service.domain.running.room.exception.AlreadyRoomPlayerException;
import com.runiverse.running_service.domain.running.room.exception.InvalidCloseAtException;
import com.runiverse.running_service.domain.running.room.exception.NotRoomPlayerException;
import com.runiverse.running_service.domain.running.room.exception.RoomNotJoinableException;
import com.runiverse.running_service.domain.running.room.exception.RunningRoomTypeRequiredException;
import com.runiverse.running_service.domain.running.room.exception.StartAtRequiredException;
import com.runiverse.running_service.domain.running.room.vo.PlayerCount;
import com.runiverse.running_service.domain.running.room.vo.RunningRoomId;
import com.runiverse.running_service.domain.running.room.vo.RunningRoomStatus;
import com.runiverse.running_service.domain.running.room.vo.RunningRoomType;
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
    private LocalDateTime closeAt;         // 방이 실제로 닫힌 시각 — 열려 있는 동안 null
    private final Distance targetDistance;       // 정해진 뒤 바뀌지 않는다
    private RunningRoomStatus status;
    private PlayerCount playerCount;
    private Pace avgPace;       // 참가자가 없으면 null — 평균 낼 대상이 없다
    private final List<RoomSession> sessions;    // 방이 맺고 있는 관계들

    //
    @Builder
    private RunningRoom(Long runningRoomId, RunningRoomType type, RunningRoomStatus status,
                        LocalDateTime startAt, LocalDateTime closeAt,
                        Integer targetDistance, Integer avgPace,
                        int currentPlayerCount, int maxPlayerCount,
                        List<SessionDraft> sessions) {
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
        validateCloseAt(this.status, closeAt);
        this.closeAt = closeAt;
        this.targetDistance = targetDistance == null ? null : new Distance(targetDistance);
        this.avgPace = avgPace == null ? null : new Pace(avgPace);
        this.playerCount = new PlayerCount(currentPlayerCount, maxPlayerCount);
        // 세션은 방을 거쳐야만 만들어진다 — 밖에서는 SessionDraft까지만 채울 수 있다
        this.sessions = new ArrayList<>();
        if (sessions != null) {
            sessions.forEach(draft -> this.sessions.add(RoomSession.from(draft)));
        }
    }


    // 솔로 — 모집 단계 없이 확정(MATCHED)된 채로 태어난다
    public static RunningRoom openSolo(UserId userId, RunningPlayerId runningPlayerId,
                                       int avgPace, Integer targetDistance, LocalDateTime startAt) {
        RunningRoom room = builder()
                .type(RunningRoomType.SOLO)
                .startAt(startAt)
                .targetDistance(targetDistance)
                .avgPace(avgPace)
                .currentPlayerCount(1)
                .maxPlayerCount(SOLO_MAX_PLAYER)
                .build();
        room.sessions.add(RoomSession.open(userId, runningPlayerId));
        return room;
    }

    // 매칭 — 항상 1인 방으로 태어나 start_at - 모집마감오프셋까지 모집한다
    public static RunningRoom openMatch(UserId userId, RunningPlayerId runningPlayerId,
                                        int avgPace, Integer targetDistance, LocalDateTime startAt) {
        RunningRoom room = builder()
                .type(RunningRoomType.MATCH)
                .startAt(startAt)
                .targetDistance(targetDistance)
                .avgPace(avgPace)
                .currentPlayerCount(1)
                .maxPlayerCount(MATCH_MAX_PLAYER)
                .build();
        room.sessions.add(RoomSession.open(userId, runningPlayerId));
        return room;
    }

    public void start() {
        this.status = status.transitionTo(RunningRoomStatus.STARTED);
    }

    // 종료 — 상태와 닫힌 시각이 한 번에 확정된다(둘이 갈라지면 조회가 거짓말을 한다)
    public void finish(LocalDateTime closeAt) {
        this.status = status.transitionTo(RunningRoomStatus.FINISHED);
        this.closeAt = closeAt;
    }

    public void cancel(LocalDateTime closeAt) {
        this.status = status.transitionTo(RunningRoomStatus.CANCELLED);
        this.closeAt = closeAt;
    }

    // 참가자 페이스는 다른 애그리거트라 application이 읽어 넘긴다
    public void recalculateAvgPace(List<Pace> paces) {
        if (paces.isEmpty()) {
            this.avgPace = null;
            return;
        }
        int sum = paces.stream().mapToInt(Pace::secondsPerKm).sum();
        this.avgPace = new Pace(sum / paces.size());
    }

    // 후보 스캔이 걸러도 애그리거트가 다시 지킨다 — 스캔과 합류 사이에 자리가 찰 수 있다
    public void join(UserId userId, RunningPlayerId runningPlayerId) {
        if (!canJoin()) {
            throw new RoomNotJoinableException();
        }
        RoomSession existing = findSession(userId).orElse(null);
        if (existing == null) {
            this.playerCount = playerCount.join();
            this.sessions.add(RoomSession.open(userId, runningPlayerId));
            return;
        }
        if (existing.isConnected()) {
            throw new AlreadyRoomPlayerException();   // 한 플레이어 = 최대 한 방
        }
        // 전에 거쳐 간 방이다 — 키가 유저라 행을 새로 만들지 않고 되살린다(erd).
        // 몇 번을 나갔든 다시 받아 준다 — 이탈 이력은 후보 순위만 낮출 뿐 문을 잠그지 않는다
        this.playerCount = playerCount.join();
        existing.reassign(runningPlayerId);
    }

    // start_at·target_distance는 조회가 등가로 거르고, 방은 모집 상태와 자리만 본다.
    // 페이스는 합류 자격이 아니라 후보를 고르는 순서다(MatchRoomAssigner)
    public boolean canJoin() {
        return status == RunningRoomStatus.MATCHING
                && playerCount.canJoin()
                // 참가자가 0이면 평균이 지워지고(erd) 그 방은 같은 순간 닫힌다 —
                // 순위를 매길 기준이 없는 방에 붙이지 않는다
                && avgPace != null;
    }

    // 모집 마감(start_at - 오프셋) 도달 — 인원 수와 무관하게 확정된다(1인이면 1인으로 확정돼 혼자 뛴다)
    public void closeMatching() {
        this.status = status.transitionTo(RunningRoomStatus.MATCHED);
    }

    // 나갔던 사람이 돌아옴 — 러닝 중에도 가능해서 join()의 모집 조건을 타지 않는다
    public void rejoin(UserId userId) {
        if (status.isTerminal()) {
            throw new RoomNotJoinableException();   // 끝났거나 취소된 방엔 돌아올 수 없다
        }
        RoomSession session = session(userId);
        if (session.isConnected()) {
            throw new AlreadyRoomPlayerException();
        }
        this.playerCount = playerCount.join();   // 그새 자리가 찼으면 RoomIsFullException
        session.rejoin();
    }

    public void leave(UserId userId, LocalDateTime leftAt) {
        RoomSession session = session(userId);                    // 1. 이 방 참가자인가
        if (!session.isConnected()) {
            throw new AlreadyLeftRoomException();                // 2. 이미 나갔는가 — 중복 이탈 방어
        }
        PlayerCount left = playerCount.leave();                  // 3. 계산만 한다, 아직 반영 안 함
        boolean lastOne = left.current() == 0;
        RunningRoomStatus nextStatus = lastOne                   // 4. 상태 전이 가능 여부까지 여기서 확인한다
                ? status.transitionTo(RunningRoomStatus.CANCELLED)
                : status;
        this.playerCount = left;                                 // 5. 여기부터 확정 — 더는 예외가 나지 않는다
        this.status = nextStatus;
        if (lastOne) {
            this.closeAt = leftAt;
        }
        session.leave();
    }

    // 완주로 자리를 비운다 — 인원도 방 상태도 건드리지 않는다.
    // leave()를 쓰면 마지막 완주자가 인원을 0으로 만들어 방이 CANCELLED가 된다(erd 생명주기)
    public void finishSession(UserId userId) {
        session(userId).finish();
    }

    // 후보가 여럿일 때 순위를 가른다 — 사람들이 잘 떠나지 않은 방이 매칭 품질이 좋다는 신호다(erd).
    // 나간 사람의 세션(is_connected=false)도 센다. 그게 곧 "떠난 사람이 많았다"는 뜻이다
    public int totalLeaveCount() {
        return sessions.stream().mapToInt(session -> session.getLeaveCount().value()).sum();
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

    public Optional<Pace> getAvgPace() {
        return Optional.ofNullable(avgPace);
    }

    public List<RoomSession> getSessions() {
        return Collections.unmodifiableList(sessions);
    }

    private RoomSession session(UserId userId) {
        return findSession(userId).orElseThrow(NotRoomPlayerException::new);
    }

    private Optional<RoomSession> findSession(UserId userId) {
        return sessions.stream().filter(session -> session.isSameUser(userId)).findFirst();
    }

    // 닫힌 시각은 종료 상태와 짝이다 — DB에서 복원할 때 어긋난 행을 여기서 잡는다
    private static void validateCloseAt(RunningRoomStatus status, LocalDateTime closeAt) {
        if (status.isTerminal() != (closeAt != null)) {
            throw new InvalidCloseAtException();
        }
    }
}
