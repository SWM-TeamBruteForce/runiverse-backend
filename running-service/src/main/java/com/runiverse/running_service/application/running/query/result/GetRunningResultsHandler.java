package com.runiverse.running_service.application.running.query.result;

import com.runiverse.running_service.application.running.command.finish.PolylineDecoder;
import com.runiverse.running_service.application.running.exception.NotRoomPlayerException;
import com.runiverse.running_service.application.running.exception.RunningResultNotFoundException;
import com.runiverse.running_service.application.running.port.in.GetRunningResultsUsecase;
import com.runiverse.running_service.application.running.port.out.LoadPlayerProfilesPort;
import com.runiverse.running_service.application.running.port.out.LoadRunningResultPlayersPort;
import com.runiverse.running_service.application.running.port.out.LoadRunningResultRecordPort;
import com.runiverse.running_service.application.running.port.out.LoadRunningRoomPort;
import com.runiverse.running_service.application.running.port.out.PlayerProfile;
import com.runiverse.running_service.application.running.port.out.RoutePoint;
import com.runiverse.running_service.application.running.port.out.RunningResultPlayer;
import com.runiverse.running_service.application.running.port.out.RunningResultRecord;
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
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GetRunningResultsHandler implements GetRunningResultsUsecase {

    private static final String DELETED_NICKNAME = "탈퇴한 사용자";
    private static final String STATUS_RUNNING = "RUNNING";
    private static final String STATUS_COMPLETED = "COMPLETED";

    // 러닝 단계에 들어간 참가자만 남긴다 — 시작 전 이탈자(MATCHED_LEFT_*)는 제외한다(api-spec 6-1)
    private static final Set<RunningPlayerStatus> RUNNING_STAGE = Set.of(
            RunningPlayerStatus.RUNNING,
            RunningPlayerStatus.RUNNING_LEFT_PENALTY,
            RunningPlayerStatus.RUNNING_LEFT_NO_PENALTY,
            RunningPlayerStatus.COMPLETED);

    private final LoadRunningRoomPort loadRunningRoomPort;
    private final LoadRunningResultPlayersPort loadRunningResultPlayersPort;
    private final LoadRunningResultRecordPort loadRunningResultRecordPort;
    private final LoadPlayerProfilesPort loadPlayerProfilesPort;
    private final GenerateViewUrlPort generateViewUrlPort;

    @Override
    public GetRunningResultsResult handle(GetRunningResultsQuery query) {
        RunningRoomId roomId = new RunningRoomId(query.runningRoomId());
        UserId viewerId = new UserId(query.viewerId());
        // 1. 없는 방과 남의 방을 나눈다 — 방이 없으면 404, 있는데 참가자가 아니면 403.
        //    순서를 뒤집으면 없는 방에 403이 나가면서 방의 존재 여부가 새어 나간다
        loadRunningRoomPort.loadById(roomId).orElseThrow(RunningResultNotFoundException::new);

        List<RunningResultPlayer> players = loadRunningResultPlayersPort.loadPlayers(roomId).stream()
                .filter(player -> RUNNING_STAGE.contains(player.status()))
                .toList();
        // 2. 러닝에 들어가지 않은 사람은 이 방의 결과를 볼 수 없다
        if (players.stream().noneMatch(player -> player.userId().equals(query.viewerId()))) {
            throw new NotRoomPlayerException();
        }

        // 3. 최상위 세 값은 본인 기록에서만 나온다 — 아직 뛰는 중이면 비어 있다(api-spec 6-1).
        //    권한 판정을 통과한 뒤에 읽는다
        Optional<RunningResultRecord> record =
                loadRunningResultRecordPort.loadRecord(roomId, viewerId);

        // 4. 프로필은 한 번에 읽는다 — 참가자마다 조회하면 인원수만큼 쿼리가 나간다
        Map<UUID, PlayerProfile> profiles = loadPlayerProfilesPort.loadProfiles(
                players.stream().map(RunningResultPlayer::userId).toList());

        return new GetRunningResultsResult(
                query.runningRoomId(),
                record.map(RunningResultRecord::startedAt).orElse(null),
                record.map(RunningResultRecord::finishedAt).orElse(null),
                record.map(RunningResultRecord::routePolyline).map(this::routes).orElse(null),
                players.stream()
                        .map(player -> toPlayer(player, profiles, query.viewerId()))
                        .toList());
    }

    // 빈 폴리라인이 저장될 일은 없지만, 빈 배열을 내려 "경로 있음"으로 오해하게 두지 않는다
    private List<RoutePoint> routes(String routePolyline) {
        List<RoutePoint> points = PolylineDecoder.decode(routePolyline);
        return points.isEmpty() ? null : points;
    }

    private GetRunningResultsResult.Player toPlayer(RunningResultPlayer player,
                                                    Map<UUID, PlayerProfile> profiles,
                                                    UUID viewerId) {
        // 기록은 남고 사용자만 사라진다 — users 행이 없으면 탈퇴다(api-spec §0)
        PlayerProfile profile = profiles.get(player.userId());
        boolean deleted = profile == null;
        return new GetRunningResultsResult.Player(
                player.userId(),
                deleted ? DELETED_NICKNAME : profile.nickname(),
                deleted ? null : profileImageUrl(profile),
                status(player.status()),
                deleted,
                player.userId().equals(viewerId),
                player.totalDistanceMeters(),
                player.totalDurationSeconds(),
                player.totalCaloriesKcal(),
                player.averagePaceSecondsPerKm(),
                player.averageCadenceSpm(),
                player.totalElevationGainMeters());
    }

    // 응답은 두 값뿐이다 — 페널티 여부는 본인 매칭 쿨다운용 내부 판정이라 남의 화면에 뿌리지 않는다.
    // RUNNING_STAGE로 이미 걸러 여기 오는 것은 넷뿐이므로, RUNNING이 아니면 전부 끝난 참가자다
    private String status(RunningPlayerStatus status) {
        return status == RunningPlayerStatus.RUNNING ? STATUS_RUNNING : STATUS_COMPLETED;
    }

    // 사진이 없으면 URL도 없다
    private String profileImageUrl(PlayerProfile profile) {
        return profile.profileImageKey() == null
                ? null
                : generateViewUrlPort.generate(profile.profileImageKey());
    }

}
