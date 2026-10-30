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
import com.runiverse.running_service.domain.running.room.RoomInvitation;
import com.runiverse.running_service.domain.running.room.RunningRoom;
import com.runiverse.running_service.domain.running.room.vo.RunningRoomId;
import com.runiverse.running_service.domain.running.room.vo.RunningRoomStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

// 스트림의 방 상태 이벤트(MATCH_*·INVITE_ROOM_*)가 같은 RoomInfo를 쓴다 — 조립은 여기 한 곳에서만 한다
@Component
@RequiredArgsConstructor
public class RoomInfoAssembler {

    private static final String DELETED_NICKNAME = "탈퇴한 사용자";
    private final LoadMatchPlayersPort loadMatchPlayersPort;
    private final LoadPlayerProfilesPort loadPlayerProfilesPort;
    private final GenerateViewUrlPort generateViewUrlPort;
    private final RecruitmentClosePolicy recruitmentClosePolicy;
    private final LoadActiveApplicationPort loadActiveApplicationPort;
    private final LoadMatchRoomPort loadMatchRoomPort;
    private final LoadMatchRoomDetailPort loadMatchRoomDetailPort;   // 잠금 없는 조회

    public RoomInfo assemble(RunningRoom room) {
        RunningRoomId roomId = room.getRunningRoomId()
                .orElseThrow(() -> new IllegalStateException("저장되지 않은 방은 조립할 수 없다"));
        List<MatchPlayer> players = loadMatchPlayersPort.loadPlayers(roomId);
        List<RoomInvitation> invitees = shownInvitees(room);
        // 프로필은 참가자·초대 대상을 합쳐 한 번에 읽는다 — 사람마다 조회하면 인원수만큼 쿼리가 나간다
        Map<UUID, PlayerProfile> profiles = loadPlayerProfilesPort.loadProfiles(
                profileTargets(players, invitees));
        return new RoomInfo(
                roomId.value(),
                room.getType(),
                room.getStatus(),
                room.getStartAt(),
                // 저장값이 아니라 계산값이다 — 오프셋을 바꾸면 진행 중인 방의 마감도 함께 움직인다
                recruitmentClosePolicy.closeAt(room.getType(), room.getStartAt()),
                room.getTargetDistance().map(Distance::meters).orElse(null),
                room.getAvgPace().map(Pace::secondsPerKm).orElse(null),
                players.stream().map(player -> toPlayer(player, profiles)).toList(),
                invitees.stream().map(invitee -> toInvitee(invitee, profiles)).toList());
    }

    // 응답 전인(PENDING) 초대만 받는 사람마다 한 건 — 보낸 사람별 행은 상태가 같아 첫 행만 쓴다.
    // 수락은 players로 옮겨 가고, 거절·나감은 현황에서 빠지며(다시 초대하면 돌아온다), 만료는 마감 뒤라 그리지 않는다.
    // 모집이 끝나면 PENDING도 만료돼 비고, 매칭 방은 초대가 없어 언제나 빈다
    private static List<RoomInvitation> shownInvitees(RunningRoom room) {
        if (room.getStatus() != RunningRoomStatus.MATCHING) {
            return List.of();
        }
        Map<UserId, RoomInvitation> byInvitee = new LinkedHashMap<>();
        room.getInvitations().stream()
                .filter(RoomInvitation::isPending)
                .forEach(invitation -> byInvitee.putIfAbsent(invitation.getInviteeId(), invitation));
        return List.copyOf(byInvitee.values());
    }

    private static Collection<UUID> profileTargets(List<MatchPlayer> players, List<RoomInvitation> invitees) {
        List<UUID> targets = new ArrayList<>(players.stream().map(MatchPlayer::userId).toList());
        invitees.forEach(invitee -> targets.add(invitee.getInviteeId().value()));
        return targets;
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

    // 받는 사람이 탈퇴하면 초대 행은 탈퇴 처리가 지워 여기 오지 않는다 — 형식상 참가자와 같게 판정한다
    private RoomInfo.RoomInvitee toInvitee(RoomInvitation invitation, Map<UUID, PlayerProfile> profiles) {
        UUID userId = invitation.getInviteeId().value();
        PlayerProfile profile = profiles.get(userId);
        boolean deleted = profile == null;
        return new RoomInfo.RoomInvitee(
                userId,
                deleted ? DELETED_NICKNAME : profile.nickname(),
                deleted ? null : profileImageUrl(profile),
                invitation.getStatus(),
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
