package com.runiverse.running_service.integration_test.fake;

import com.runiverse.running_service.application.match.port.out.CreateMatchApplicationPort;
import com.runiverse.running_service.application.match.port.out.CreateMatchRoomPort;
import com.runiverse.running_service.application.match.port.out.ExistsActiveApplicationPort;
import com.runiverse.running_service.application.match.port.out.LoadActiveApplicationPort;
import com.runiverse.running_service.application.match.port.out.LoadMatchRoomDetailPort;
import com.runiverse.running_service.application.match.port.out.LockMatchApplicationPort;
import com.runiverse.running_service.application.match.port.out.LockMatchRoomPort;
import com.runiverse.running_service.application.match.port.out.UpdateMatchApplicationPort;
import com.runiverse.running_service.application.match.port.out.UpdateMatchRoomPort;
import com.runiverse.running_service.application.running.port.out.CountStartedRunningPlayerPort;
import com.runiverse.running_service.application.running.port.out.CreateRunningPlayerPort;
import com.runiverse.running_service.application.running.port.out.CreateRunningRoomPort;
import com.runiverse.running_service.application.running.port.out.ExistsActiveRunningPlayerPort;
import com.runiverse.running_service.application.running.port.out.ExistsRunningPlayerPort;
import com.runiverse.running_service.application.running.port.out.LoadRoomPlayerPort;
import com.runiverse.running_service.application.running.port.out.LoadRunningRoomPort;
import com.runiverse.running_service.application.running.port.out.LoadUserStatusPort;
import com.runiverse.running_service.application.running.port.out.LockRunningPlayerPort;
import com.runiverse.running_service.application.running.port.out.LockRunningRoomPort;
import com.runiverse.running_service.application.running.port.out.UpdateRunningPlayerPort;
import com.runiverse.running_service.application.running.port.out.UpdateRunningRoomPort;
import com.runiverse.running_service.application.running.port.out.UserStatusRow;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.running.metric.vo.Distance;
import com.runiverse.running_service.domain.running.metric.vo.Pace;
import com.runiverse.running_service.domain.running.player.RunningPlayer;
import com.runiverse.running_service.domain.running.player.vo.RunningPlayerStatus;
import com.runiverse.running_service.domain.running.room.RunningRoom;
import com.runiverse.running_service.domain.running.room.SessionDraft;
import com.runiverse.running_service.domain.running.room.vo.RunningRoomId;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

// RunningPersistenceAdapter를 대신한다 — 실제 어댑터처럼 bigserial ID를 채워 돌려준다.
// 잠금 포트도 함께 구현하지만 단일 스레드 맵이라 실제로 잠그지는 않는다 —
// 락 동작 자체는 여기서 검증할 수 없고, 잠긴 뒤의 판정만 본다
public class InMemoryRunningStore implements CreateRunningPlayerPort, CreateRunningRoomPort,
        ExistsActiveRunningPlayerPort, LoadRunningRoomPort, LockRunningRoomPort, UpdateRunningRoomPort,
        LockRunningPlayerPort, UpdateRunningPlayerPort, LoadRoomPlayerPort,
        ExistsRunningPlayerPort, CountStartedRunningPlayerPort, LoadUserStatusPort,
        // 실제 어댑터처럼 매칭 유스케이스의 포트도 같은 메서드로 만족시킨다
        CreateMatchApplicationPort, ExistsActiveApplicationPort,
        CreateMatchRoomPort, UpdateMatchRoomPort, LockMatchRoomPort,
        LoadActiveApplicationPort, LockMatchApplicationPort, UpdateMatchApplicationPort,
        LoadMatchRoomDetailPort {

    private final Map<Long, RunningPlayer> players = new LinkedHashMap<>();
    private final Map<Long, RunningRoom> rooms = new LinkedHashMap<>();
    // bigserial은 1부터 시작한다
    private long nextPlayerId = 1L;
    private long nextRoomId = 1L;

    @Override
    public RunningPlayer create(RunningPlayer player) {
        if (!player.isNew()) {
            throw new IllegalStateException("이미 저장된 신청이다 — 종료·상태 변경은 별도 포트로 처리한다");
        }
        long id = nextPlayerId++;
        RunningPlayer saved = copyWithId(player, id);
        players.put(id, saved);
        return saved;
    }

    @Override
    public RunningRoom create(RunningRoom room) {
        if (!room.isNew()) {
            throw new IllegalStateException("이미 저장된 방이다 — 상태 변경은 별도 포트로 처리한다");
        }
        long id = nextRoomId++;
        RunningRoom saved = copyWithId(room, id);
        rooms.put(id, saved);
        return saved;
    }

    // 실제 쿼리와 같이 deleted_at IS NULL인 신청만 "진행 중"으로 본다
    @Override
    public boolean existsActive(UserId userId) {
        return players.values().stream()
                .anyMatch(player -> player.getUserId().equals(userId) && player.isActive());
    }

    // 어댑터의 toDomain처럼 분리된 객체를 돌려준다 —
    // 저장본을 그대로 주면 update()를 빼먹어도 변경이 반영돼 테스트가 거짓으로 통과한다
    @Override
    public Optional<RunningRoom> loadById(RunningRoomId runningRoomId) {
        return Optional.ofNullable(rooms.get(runningRoomId.value()))
                .map(room -> copyWithId(room, runningRoomId.value()));
    }

    // 어댑터의 lockById처럼 조건은 loadById와 같다 — 여기서 다른 것은 잠그지 않는다는 점뿐이다
    @Override
    public Optional<RunningRoom> lockById(RunningRoomId runningRoomId) {
        return loadById(runningRoomId);
    }

    @Override
    public Optional<RunningPlayer> lockActive(UserId userId) {
        return loadActive(userId);
    }

    @Override
    public Optional<RunningPlayer> loadActive(UserId userId) {
        return players.values().stream()
                .filter(player -> player.getUserId().equals(userId) && player.isActive())
                .findFirst()
                .map(player -> copyWithId(player, player.getRunningPlayerId().orElseThrow().value()));
    }

    @Override
    public Optional<RunningRoom> loadDetailById(RunningRoomId runningRoomId) {
        return loadById(runningRoomId);
    }

    // 실제 어댑터처럼 세션을 거쳐 방의 활성 참가자를 user_id 순으로 찾는다 — 잠그지는 않는다
    @Override
    public List<RunningPlayer> lockActiveInRoom(RunningRoomId runningRoomId) {
        return playersOf(runningRoomId)
                .filter(RunningPlayer::isActive)
                .sorted(Comparator.comparing(player -> player.getUserId().value()))
                .map(player -> copyWithId(player, player.getRunningPlayerId().orElseThrow().value()))
                .toList();
    }

    // load와 같다 — 잠그지는 않는다
    @Override
    public Optional<RunningPlayer> lockInRoom(RunningRoomId runningRoomId, UserId userId) {
        return load(runningRoomId, userId);
    }

    // 실제 어댑터처럼 세션을 거쳐 방의 참가자를 찾는다.
    // deleted_at은 보지 않는다 — 이미 종료된 참가자도 찾아야 RUNNING_FINISH가 멱등이 된다
    @Override
    public Optional<RunningPlayer> load(RunningRoomId runningRoomId, UserId userId) {
        return playersOf(runningRoomId)
                .filter(player -> player.getUserId().equals(userId))
                .findFirst()
                .map(player -> copyWithId(player, player.getRunningPlayerId().orElseThrow().value()));
    }

    @Override
    public boolean existsRunning(RunningRoomId runningRoomId) {
        return playersOf(runningRoomId)
                .anyMatch(player -> player.getStatus() == RunningPlayerStatus.RUNNING);
    }

    @Override
    public int countStartedRunning(RunningRoomId runningRoomId) {
        return (int) playersOf(runningRoomId)
                .filter(player -> player.getStatus().hasStartedRunning())
                .count();
    }

    // 목표 거리는 방 값을 쓴다 — 솔로의 목표 없음(null)은 방에만 남는다
    @Override
    public Optional<UserStatusRow> loadStatus(UserId userId) {
        return rooms.values().stream()
                .flatMap(room -> room.getSessions().stream()
                        .filter(session -> session.isConnected())
                        .map(session -> players.get(session.getRunningPlayerId().value()))
                        .filter(player -> player != null
                                && player.getUserId().equals(userId) && player.isActive())
                        .map(player -> new UserStatusRow(
                                room.getRunningRoomId().orElseThrow().value(),
                                room.getType(),
                                room.getStatus(),
                                room.getStartAt(),
                                room.getTargetDistance().map(Distance::meters).orElse(null))))
                .findFirst();
    }

    private Stream<RunningPlayer> playersOf(RunningRoomId runningRoomId) {
        RunningRoom room = rooms.get(runningRoomId.value());
        if (room == null) {
            return Stream.empty();
        }
        return room.getSessions().stream()
                .map(session -> players.get(session.getRunningPlayerId().value()))
                .filter(Objects::nonNull);
    }

    @Override
    public void update(RunningRoom room) {
        long id = room.getRunningRoomId()
                .orElseThrow(() -> new IllegalStateException("저장되지 않은 방은 갱신할 수 없다"))
                .value();
        if (!rooms.containsKey(id)) {
            throw new IllegalStateException("없는 방은 갱신할 수 없다");
        }
        rooms.put(id, copyWithId(room, id));
    }

    @Override
    public void update(RunningPlayer player) {
        long id = player.getRunningPlayerId()
                .orElseThrow(() -> new IllegalStateException("저장되지 않은 신청은 갱신할 수 없다"))
                .value();
        if (!players.containsKey(id)) {
            throw new IllegalStateException("없는 신청은 갱신할 수 없다");
        }
        players.put(id, copyWithId(player, id));
    }

    private RunningPlayer copyWithId(RunningPlayer player, long id) {
        return RunningPlayer.builder()
                .runningPlayerId(id)
                .userId(player.getUserId().value())
                .status(player.getStatus())
                .avgPace(player.getAvgPace().secondsPerKm())
                .targetDistance(player.getTargetDistance().meters())
                .desiredPlayerCount(player.getDesiredPlayerCount().value())
                .startAt(player.getStartAt())
                .deletedAt(player.getDeletedAt().orElse(null))
                .build();
    }

    private RunningRoom copyWithId(RunningRoom room, long id) {
        List<SessionDraft> sessions = new ArrayList<>();
        room.getSessions().forEach(session -> sessions.add(new SessionDraft(
                session.getUserId(), session.getRunningPlayerId(),
                session.getLeaveCount().value(), session.isConnected())));
        return RunningRoom.builder()
                .runningRoomId(id)
                .type(room.getType())
                .status(room.getStatus())
                .startAt(room.getStartAt())
                .closeAt(room.getCloseAt().orElse(null))
                .targetDistance(room.getTargetDistance().map(Distance::meters).orElse(null))
                .avgPace(room.getAvgPace().map(Pace::secondsPerKm).orElse(null))
                .currentPlayerCount(room.getPlayerCount().current())
                .maxPlayerCount(room.getPlayerCount().max())
                .sessions(sessions)
                .build();
    }

    // 검증 전용
    public Optional<RunningRoom> findRoom(Long runningRoomId) {
        return Optional.ofNullable(rooms.get(runningRoomId));
    }

    public Optional<RunningPlayer> findPlayer(Long runningPlayerId) {
        return Optional.ofNullable(players.get(runningPlayerId));
    }

    public int playerCount() {
        return players.size();
    }

    public List<RunningRoom> findAllRooms() {
        return List.copyOf(rooms.values());
    }

    public int roomCount() {
        return rooms.size();
    }
}
