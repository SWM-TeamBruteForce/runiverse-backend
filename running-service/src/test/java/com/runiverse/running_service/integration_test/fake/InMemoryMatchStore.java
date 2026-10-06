package com.runiverse.running_service.integration_test.fake;

import com.runiverse.running_service.application.match.port.out.LoadMatchCandidatesPort;
import com.runiverse.running_service.application.match.port.out.LoadMatchPlayersPort;
import com.runiverse.running_service.application.match.port.out.LoadMatchRoomPort;
import com.runiverse.running_service.application.match.port.out.MatchCandidate;
import com.runiverse.running_service.application.match.port.out.MatchPlayer;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.running.metric.vo.Distance;
import com.runiverse.running_service.domain.running.room.RoomSession;
import com.runiverse.running_service.domain.running.room.RunningRoom;
import com.runiverse.running_service.domain.running.room.vo.RunningRoomId;
import com.runiverse.running_service.domain.running.room.vo.RunningRoomStatus;
import com.runiverse.running_service.domain.running.room.vo.RunningRoomType;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

// MatchPersistenceAdapter를 대신한다 — 방 숨김(deleted_at)은 도메인에 없어 걸러내지 않는다
public class InMemoryMatchStore implements LoadMatchRoomPort, LoadMatchPlayersPort, LoadMatchCandidatesPort {

    private final InMemoryRunningStore runningStore;

    public InMemoryMatchStore(InMemoryRunningStore runningStore) {
        this.runningStore = runningStore;
    }

    @Override
    public Optional<RunningRoomId> findAssignedRoom(UserId userId) {
        return runningStore.findAllRooms().stream()
                .filter(room -> room.getSessions().stream()
                        .anyMatch(session -> session.isConnected() && session.getUserId().equals(userId)))
                .findFirst()
                .map(room -> room.getRunningRoomId().orElseThrow());
    }

    @Override
    public List<MatchPlayer> loadPlayers(RunningRoomId runningRoomId) {
        return runningStore.findRoom(runningRoomId.value()).stream()
                .flatMap(room -> room.getSessions().stream())
                .filter(RoomSession::isConnected)
                .map(session -> runningStore.findPlayer(session.getRunningPlayerId().value()))
                .flatMap(Optional::stream)
                // 실제 쿼리의 ORDER BY p.runningPlayerId
                .sorted(Comparator.comparing(player -> player.getRunningPlayerId().orElseThrow().value()))
                .map(player -> new MatchPlayer(player.getUserId().value(), player.getAvgPace().secondsPerKm()))
                .toList();
    }

    @Override
    public List<MatchCandidate> loadCandidates(UserId userId, LocalDateTime startAt,
                                               int targetDistanceMeters) {
        return runningStore.findAllRooms().stream()
                .filter(room -> room.getType() == RunningRoomType.MATCH
                        && room.getStatus() == RunningRoomStatus.MATCHING
                        && room.getStartAt().equals(startAt)
                        && room.getTargetDistance().map(Distance::meters)
                        .filter(meters -> meters == targetDistanceMeters).isPresent()
                        && room.getPlayerCount().current() < room.getPlayerCount().max()
                        && room.getAvgPace().isPresent())
                .map(room -> new MatchCandidate(
                        room.getRunningRoomId().orElseThrow().value(),
                        room.getAvgPace().orElseThrow().secondsPerKm(),
                        myLeaveCount(room, userId)))
                .toList();
    }

    private int myLeaveCount(RunningRoom room, UserId userId) {
        return room.getSessions().stream()
                .filter(session -> session.getUserId().equals(userId))
                .map(session -> session.getLeaveCount().value())
                .findFirst()
                .orElse(0);
    }
}
