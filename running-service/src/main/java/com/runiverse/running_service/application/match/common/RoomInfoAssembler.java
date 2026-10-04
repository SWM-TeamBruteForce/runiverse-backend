package com.runiverse.running_service.application.match.common;

import com.runiverse.running_service.application.common.port.out.LoadPlayerProfilesPort;
import com.runiverse.running_service.application.common.port.out.PlayerProfile;
import com.runiverse.running_service.application.match.port.out.LoadActiveApplicationPort;
import com.runiverse.running_service.application.match.port.out.LoadMatchPlayersPort;
import com.runiverse.running_service.application.match.port.out.LoadMatchRoomDetailPort;
import com.runiverse.running_service.application.match.port.out.LoadMatchRoomPort;
import com.runiverse.running_service.application.match.port.out.MatchPlayer;
import com.runiverse.running_service.application.match.port.out.RoomInfo;
import com.runiverse.running_service.application.user.port.out.GenerateViewUrlPort;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.running.metric.vo.Distance;
import com.runiverse.running_service.domain.running.metric.vo.Pace;
import com.runiverse.running_service.domain.running.room.RunningRoom;
import com.runiverse.running_service.domain.running.room.vo.RunningRoomId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

// 스트림의 MATCH_STARTED·MATCH_ROOM_UPDATED가 같은 RoomInfo를 쓴다 — 조립은 여기 한 곳에서만 한다
@Component
@RequiredArgsConstructor
public class RoomInfoAssembler {

    private static final String DELETED_NICKNAME = "탈퇴한 사용자";
    private final LoadMatchPlayersPort loadMatchPlayersPort;
    private final LoadPlayerProfilesPort loadPlayerProfilesPort;
    private final GenerateViewUrlPort generateViewUrlPort;
    private final MatchProperties matchProperties;
    private final LoadActiveApplicationPort loadActiveApplicationPort;
    private final LoadMatchRoomPort loadMatchRoomPort;
    private final LoadMatchRoomDetailPort loadMatchRoomDetailPort;   // 잠금 없는 조회

    public RoomInfo assemble(RunningRoom room) {
        RunningRoomId roomId = room.getRunningRoomId()
                .orElseThrow(() -> new IllegalStateException("저장되지 않은 방은 조립할 수 없다"));
        List<MatchPlayer> players = loadMatchPlayersPort.loadPlayers(roomId);
        // 프로필은 한 번에 읽는다 — 참가자마다 조회하면 인원수만큼 쿼리가 나간다
        Map<UUID, PlayerProfile> profiles = loadPlayerProfilesPort.loadProfiles(
                players.stream().map(MatchPlayer::userId).toList());
        return new RoomInfo(
                roomId.value(),
                room.getStatus(),
                room.getStartAt(),
                // 저장값이 아니라 계산값이다 — 오프셋을 바꾸면 진행 중인 방의 마감도 함께 움직인다
                room.getStartAt().minus(matchProperties.closeOffset()),
                room.getTargetDistance().map(Distance::meters).orElse(null),
                room.getAvgPace().map(Pace::secondsPerKm).orElse(null),
                players.stream().map(player -> toPlayer(player, profiles)).toList());
    }

    private RoomInfo.RoomPlayer toPlayer(MatchPlayer player, Map<UUID, PlayerProfile> profiles) {
        // 신청은 남고 사용자만 사라진다 — users 행이 없으면 탈퇴다
        PlayerProfile profile = profiles.get(player.userId());
        boolean deleted = profile == null;
        return new RoomInfo.RoomPlayer(
                player.userId(),
                deleted ? DELETED_NICKNAME : profile.nickname(),
                deleted ? null : profileImageUrl(profile),
                deleted ? null : profile.introduction(),
                player.avgPaceSecondsPerKm(),
                deleted);
    }

    // 사진이 없으면 URL도 없다
    private String profileImageUrl(PlayerProfile profile) {
        return profile.profileImageKey() == null
                ? null
                : generateViewUrlPort.generate(profile.profileImageKey());
    }

    // 스트림에 붙은 유저의 현재 방을 찾아 조립한다 — 활성 신청이 없으면 비어 있다.
    // 연결 직후 스냅샷이 쓴다
    public Optional<RoomInfo> assembleFor(UserId userId) {
        return loadActiveApplicationPort.loadActive(userId)
                .flatMap(player -> loadMatchRoomPort.findAssignedRoom(userId))
                .flatMap(loadMatchRoomDetailPort::loadDetailById)
                .map(this::assemble);
    }
}
