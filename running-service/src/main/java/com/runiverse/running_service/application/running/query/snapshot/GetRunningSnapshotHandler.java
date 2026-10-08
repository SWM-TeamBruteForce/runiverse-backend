package com.runiverse.running_service.application.running.query.snapshot;

import com.runiverse.running_service.application.common.port.out.LoadPlayerProfilesPort;
import com.runiverse.running_service.application.common.port.out.PlayerProfile;
import com.runiverse.running_service.application.running.command.combo.RunningComboReader;
import com.runiverse.running_service.application.running.exception.RunningRoomNotFoundException;
import com.runiverse.running_service.application.running.port.in.GetRunningSnapshotUsecase;
import com.runiverse.running_service.application.running.port.out.LiveRunningStatus;
import com.runiverse.running_service.application.running.port.out.LoadLiveRunningStatusPort;
import com.runiverse.running_service.application.running.port.out.LoadRunningDistancePort;
import com.runiverse.running_service.application.running.port.out.LoadRunningRoomPort;
import com.runiverse.running_service.application.running.port.out.RunningDistance;
import com.runiverse.running_service.application.user.port.out.GenerateViewUrlPort;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.running.metric.vo.Distance;
import com.runiverse.running_service.domain.running.room.RoomSession;
import com.runiverse.running_service.domain.running.room.RunningRoom;
import com.runiverse.running_service.domain.running.room.vo.RunningRoomId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GetRunningSnapshotHandler implements GetRunningSnapshotUsecase {

    private static final String DELETED_NICKNAME = "탈퇴한 사용자";

    private final LoadRunningRoomPort loadRunningRoomPort;
    private final LoadPlayerProfilesPort loadPlayerProfilesPort;
    private final GenerateViewUrlPort generateViewUrlPort;
    private final LoadRunningDistancePort loadRunningDistancePort;
    private final LoadLiveRunningStatusPort loadLiveRunningStatusPort;
    private final RunningComboReader runningComboReader;

    @Override
    public GetRunningSnapshotResult handle(GetRunningSnapshotQuery query) {
        RunningRoom room = loadRunningRoomPort.loadById(new RunningRoomId(query.runningRoomId()))
                .orElseThrow(RunningRoomNotFoundException::new);
        // 이탈·완주한 참가자는 세션이 끊겨 있다 — 러닝 화면에 그들을 그릴 자리가 없다.
        // 연결만 끊긴 참가자는 is_connected를 건드리지 않으므로 여기 남아 이어 뛴다
        List<UserId> actives = room.getSessions().stream()
                .filter(RoomSession::isConnected)
                .map(RoomSession::getUserId)
                .toList();
        // 프로필은 한 번에 읽는다 — 참가자마다 조회하면 인원수만큼 쿼리가 나간다
        Map<UUID, PlayerProfile> profiles = loadPlayerProfilesPort.loadProfiles(
                actives.stream().map(UserId::value).toList());
        return new GetRunningSnapshotResult(
                query.runningRoomId(),
                room.getStartAt(),
                room.getTargetDistance().map(Distance::meters).orElse(null),
                actives.stream()
                        .map(userId -> toPlayer(query.runningRoomId(), userId, profiles))
                        .toList(),
                runningComboReader.read(query.runningRoomId(), new UserId(query.userId())).stream()
                        // 끝난 참가자의 콤보는 판정이 신선도로 떼어 낼 때까지 남는다 — 목록에 없는 상대는 싣지 않는다
                        .filter(peer -> actives.contains(new UserId(peer.userId())))
                        .toList());
    }

    // 본인도 담는다 — 앱 재설치로 로컬 트랙이 사라지면 본인 누적 거리를 복구할 경로가 이것뿐이다
    private GetRunningSnapshotResult.Player toPlayer(
            Long runningRoomId, UserId userId, Map<UUID, PlayerProfile> profiles) {
        RunningDistance distance = loadRunningDistancePort.loadDistance(runningRoomId, userId);
        // 신청은 남고 사용자만 사라진다 — users 행이 없으면 탈퇴다
        PlayerProfile profile = profiles.get(userId.value());
        boolean deleted = profile == null;
        return new GetRunningSnapshotResult.Player(
                userId.value(),
                deleted ? DELETED_NICKNAME : profile.nickname(),
                deleted ? null : profileImageUrl(profile),
                distance.metersRounded(),
                distance.lastPaceSecondsPerKm(),
                // 상태가 없으면 한 번도 붙지 않은 참가자다 — 시작 시각에 앱을 안 켠 매칭 참가자
                loadLiveRunningStatusPort.load(runningRoomId, userId)
                        .orElse(LiveRunningStatus.DISCONNECTED));
    }

    // 사진이 없으면 URL도 없다
    private String profileImageUrl(PlayerProfile profile) {
        return profile.profileImageKey() == null
                ? null
                : generateViewUrlPort.generate(profile.profileImageKey());
    }
}
