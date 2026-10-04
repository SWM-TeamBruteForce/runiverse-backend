package com.runiverse.running_service.application.running.query.split;

import com.runiverse.running_service.application.common.port.out.LoadPlayerProfilesPort;
import com.runiverse.running_service.application.common.port.out.PlayerProfile;
import com.runiverse.running_service.application.running.common.PolylineDecoder;
import com.runiverse.running_service.application.running.common.RunningFinishProperties;
import com.runiverse.running_service.application.running.exception.NotRoomPlayerException;
import com.runiverse.running_service.application.running.exception.RunningResultNotFoundException;
import com.runiverse.running_service.application.running.port.in.GetRunningSplitResultsUsecase;
import com.runiverse.running_service.application.running.port.out.LoadRunningResultPlayersPort;
import com.runiverse.running_service.application.running.port.out.LoadRunningResultRecordPort;
import com.runiverse.running_service.application.running.port.out.LoadRunningRoomPort;
import com.runiverse.running_service.application.running.port.out.LoadRunningSplitsPort;
import com.runiverse.running_service.application.running.port.out.RoutePoint;
import com.runiverse.running_service.application.running.port.out.RunningResultPlayer;
import com.runiverse.running_service.application.running.port.out.RunningResultRecord;
import com.runiverse.running_service.application.running.port.out.RunningSplitRow;
import com.runiverse.running_service.application.user.port.out.GenerateViewUrlPort;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.running.player.vo.RunningPlayerStatus;
import com.runiverse.running_service.domain.running.room.vo.RunningRoomId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GetRunningSplitResultsHandler implements GetRunningSplitResultsUsecase {

    private static final String DELETED_NICKNAME = "탈퇴한 사용자";
    private static final String STATUS_RUNNING = "RUNNING";
    private static final String STATUS_COMPLETED = "COMPLETED";

    private final LoadRunningRoomPort loadRunningRoomPort;
    private final LoadRunningResultPlayersPort loadRunningResultPlayersPort;
    private final LoadRunningResultRecordPort loadRunningResultRecordPort;
    private final LoadRunningSplitsPort loadRunningSplitsPort;
    private final LoadPlayerProfilesPort loadPlayerProfilesPort;
    private final GenerateViewUrlPort generateViewUrlPort;
    private final RunningFinishProperties properties;

    @Override
    public GetRunningSplitResultsResult handle(GetRunningSplitResultsQuery query) {
        // 방 ID는 서버가 발급한 참조라 1 미만은 없는 방이다 — VO 검증(500)까지 보내지 않는다
        if (query.runningRoomId() < 1) {
            throw new RunningResultNotFoundException();
        }
        RunningRoomId roomId = new RunningRoomId(query.runningRoomId());
        UserId viewerId = new UserId(query.viewerId());
        // 1. 없는 방과 남의 방을 나눈다 — 6-1과 같은 순서다
        loadRunningRoomPort.loadById(roomId).orElseThrow(RunningResultNotFoundException::new);

        List<RunningResultPlayer> roomPlayers = loadRunningResultPlayersPort.loadPlayers(roomId)
                .stream()
                .filter(player -> player.status().hasStartedRunning())
                .toList();
        if (roomPlayers.stream().noneMatch(player -> player.userId().equals(query.viewerId()))) {
            throw new NotRoomPlayerException();
        }
        // 2. 방 전체 구간을 한 번에 긁는다 — 구간마다 조회하면 500번 나간다
        List<RunningSplitRow> splitRows = loadRunningSplitsPort.loadSplits(roomId);
        // 3. 기록이 없는 참가자는 양쪽 players에서 모두 뺀다.
        //    구간은 기록에 딸린 행이라, 구간이 하나도 없으면 기록이 없는 것이다
        Set<UUID> recorded = splitRows.stream()
                .map(RunningSplitRow::userId)
                .collect(Collectors.toUnmodifiableSet());
        List<RunningResultPlayer> players = roomPlayers.stream()
                .filter(player -> recorded.contains(player.userId()))
                .toList();

        Map<UUID, PlayerProfile> profiles = loadPlayerProfilesPort.loadProfiles(
                players.stream().map(RunningResultPlayer::userId).toList());
        Optional<RunningResultRecord> record =
                loadRunningResultRecordPort.loadRecord(roomId, viewerId);
        // 4. 폴리라인은 한 번만 푼다 — 구간마다 풀면 500번 디코딩한다
        List<RoutePoint> decoded = record
                .map(RunningResultRecord::routePolyline)
                .map(PolylineDecoder::decode)
                .orElse(List.of());

        return new GetRunningSplitResultsResult(
                query.runningRoomId(),
                properties.splitDistanceMeters(),
                record.map(RunningResultRecord::totalDistanceMeters).orElse(null),
                record.flatMap(found ->
                        Optional.ofNullable(found.totalElevationGainMeters())).orElse(null),
                record.map(RunningResultRecord::startedAt).orElse(null),
                record.map(RunningResultRecord::finishedAt).orElse(null),
                players.stream()
                        .map(player -> toPlayer(player, profiles, query.viewerId()))
                        .toList(),
                toSplits(splitRows, decoded, query.viewerId()));
    }

    // splitNumber가 곧 같은 거리 구간이다 — 그걸로 묶으면 그대로 참가자 비교표가 된다.
    // TreeMap이라 응답의 splits가 구간 번호 오름차순으로 나간다
    private List<GetRunningSplitResultsResult.Split> toSplits(List<RunningSplitRow> splitRows,
                                                              List<RoutePoint> decoded,
                                                              UUID viewerId) {
        Map<Integer, List<RunningSplitRow>> byNumber = splitRows.stream()
                .collect(Collectors.groupingBy(
                        RunningSplitRow::splitNumber, TreeMap::new, Collectors.toList()));
        return byNumber.entrySet().stream()
                .map(entry -> toSplit(entry.getKey(), entry.getValue(), decoded, viewerId))
                .toList();
    }

    private GetRunningSplitResultsResult.Split toSplit(int splitNumber,
                                                       List<RunningSplitRow> rows,
                                                       List<RoutePoint> decoded,
                                                       UUID viewerId) {
        // 경로는 본인 것만이다 — route_start_index는 각자 자기 폴리라인을 가리킨다
        Optional<RunningSplitRow> mine = rows.stream()
                .filter(row -> row.userId().equals(viewerId))
                .findFirst();
        // 구간 경계는 방 전체가 공유하는 고정 경계라 번호로 계산된다 — DB에 없는 값이다
        int startDistanceMeters = (splitNumber - 1) * properties.splitDistanceMeters();
        // 구간 거리는 참가자와 무관하게 같다 — 내 행이 없으면 아무 행에서 가져와도 된다
        int distanceMeters = mine.map(RunningSplitRow::distanceMeters)
                .orElseGet(() -> rows.get(0).distanceMeters());
        return new GetRunningSplitResultsResult.Split(
                splitNumber,
                startDistanceMeters,
                startDistanceMeters + distanceMeters,
                distanceMeters,
                mine.map(row -> routes(decoded, row)).orElse(null),
                rows.stream().map(this::toSplitPlayer).toList());
    }

    // route_end_index는 포함인데 subList의 끝은 배타적이다 —
    // +1을 빼먹으면 구간마다 끝점이 하나씩 잘려 경계가 어긋난다
    private List<RoutePoint> routes(List<RoutePoint> decoded, RunningSplitRow row) {
        int end = Math.min(row.routeEndIndex() + 1, decoded.size());
        if (row.routeStartIndex() >= end) {
            return null;
        }
        return List.copyOf(decoded.subList(row.routeStartIndex(), end));
    }

    private GetRunningSplitResultsResult.SplitPlayer toSplitPlayer(RunningSplitRow row) {
        return new GetRunningSplitResultsResult.SplitPlayer(
                row.userId(),
                row.durationSeconds(),
                row.averagePaceSecondsPerKm(),
                row.averageCadenceSpm(),
                row.caloriesKcal(),
                row.elevationChangeMeters());
    }

    private GetRunningSplitResultsResult.Player toPlayer(RunningResultPlayer player,
                                                         Map<UUID, PlayerProfile> profiles,
                                                         UUID viewerId) {
        PlayerProfile profile = profiles.get(player.userId());
        boolean deleted = profile == null;
        return new GetRunningSplitResultsResult.Player(
                player.userId(),
                deleted ? DELETED_NICKNAME : profile.nickname(),
                deleted ? null : profileImageUrl(profile),
                player.status() == RunningPlayerStatus.RUNNING ? STATUS_RUNNING : STATUS_COMPLETED,
                deleted,
                player.userId().equals(viewerId));
    }

    private String profileImageUrl(PlayerProfile profile) {
        return profile.profileImageKey() == null
                ? null
                : generateViewUrlPort.generate(profile.profileImageKey());
    }
}
