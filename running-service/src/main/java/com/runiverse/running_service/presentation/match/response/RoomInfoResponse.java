package com.runiverse.running_service.presentation.match.response;

import com.runiverse.running_service.application.match.port.out.RoomInfo;
import com.runiverse.running_service.domain.running.room.vo.RoomInvitationStatus;
import com.runiverse.running_service.domain.running.room.vo.RunningRoomStatus;
import com.runiverse.running_service.domain.running.room.vo.RunningRoomType;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

// SSE의 방 상태 이벤트(MATCH_STARTED·MATCH_ROOM_UPDATED·INVITE_ROOM_UPDATED)가 data로 이 구조를 쓴다.
// INVITE_ROOM_ENTRANCE는 같은 필드에 startsInMs를 더한 InviteRoomEntranceResponse를 쓴다.
// 와이어 계약이라 presentation이 갖는다 — SSE 전송도 이걸 실어 보낸다
public record RoomInfoResponse(
        Long runningRoomId,
        RunningRoomType type,
        RunningRoomStatus status,
        LocalDateTime scheduledStartAt,
        LocalDateTime closeAt,
        Integer targetDistanceMeters,
        Integer teamAveragePaceSecondsPerKm,
        List<PlayerResponse> players,
        // 매칭 방과 모집이 끝난 초대 방은 빈 배열이다 — null로 내리지 않는다
        List<InvitationResponse> invitations
) {

    public static RoomInfoResponse from(RoomInfo room) {
        return new RoomInfoResponse(
                room.runningRoomId(),
                room.type(),
                room.status(),
                room.scheduledStartAt(),
                room.closeAt(),
                room.targetDistanceMeters(),
                room.teamAveragePaceSecondsPerKm(),
                room.players().stream().map(PlayerResponse::from).toList(),
                room.invitations().stream().map(InvitationResponse::from).toList());
    }

    public record PlayerResponse(
            UUID userId,
            String nickname,
            String profileImageUrl,
            String introduction,
            int averagePaceSecondsPerKm,
            boolean isDeleted
    ) {

        public static PlayerResponse from(RoomInfo.RoomPlayer player) {
            return new PlayerResponse(
                    player.userId(),
                    player.nickname(),
                    player.profileImageUrl(),
                    player.introduction(),
                    player.averagePaceSecondsPerKm(),
                    player.isDeleted());
        }
    }

    // 응답 전인(PENDING) 초대만 나간다 — 수락한 사람은 players로 옮겨 가고 거절·나감은 빠진다
    public record InvitationResponse(
            UUID userId,
            String nickname,
            String profileImageUrl,
            RoomInvitationStatus status,
            boolean isDeleted
    ) {

        public static InvitationResponse from(RoomInfo.RoomInvitee invitee) {
            return new InvitationResponse(
                    invitee.userId(),
                    invitee.nickname(),
                    invitee.profileImageUrl(),
                    invitee.status(),
                    invitee.isDeleted());
        }
    }
}
