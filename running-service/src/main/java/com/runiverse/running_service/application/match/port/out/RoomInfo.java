package com.runiverse.running_service.application.match.port.out;

import com.runiverse.running_service.domain.running.room.vo.RoomInvitationStatus;
import com.runiverse.running_service.domain.running.room.vo.RunningRoomStatus;
import com.runiverse.running_service.domain.running.room.vo.RunningRoomType;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record RoomInfo(
        Long runningRoomId,
        // 클라가 매칭 방·초대 방 화면을 가른다 — 이벤트 이름도 이 값으로 고른다
        RunningRoomType type,
        RunningRoomStatus status,
        LocalDateTime scheduledStartAt,
        // 모집 마감 시각 — start_at - 운영 오프셋으로 계산한다.
        // running_rooms.close_at(방이 실제로 닫힌 시각)과 이름이 겹치니 주의
        LocalDateTime closeAt,
        Integer targetDistanceMeters,
        // 시작 전에 참가자가 0이 되면 null — 평균 낼 대상이 없다
        Integer teamAveragePaceSecondsPerKm,
        List<RoomPlayer> players,
        // 초대 방에서 아직 참가자가 아닌 초대만 — 매칭 방과 모집이 끝난 초대 방은 빈 목록이다
        List<RoomInvitee> invitations
) {

    public record RoomPlayer(
            UUID userId,
            String nickname,
            String profileImageUrl,
            String introduction,
            int averagePaceSecondsPerKm,
            // 탈퇴하면 users 행이 지워져 프로필 조회에서 빠진다
            boolean isDeleted
    ) {

    }

    // 받는 사람 한 명당 한 건 — 보낸 사람이 여럿이어도 상태는 받는 사람 기준이라 하나다
    public record RoomInvitee(
            UUID userId,
            String nickname,
            String profileImageUrl,
            // 응답 전인 초대만 실려 지금은 늘 PENDING이다 — 응답 모양을 유지하려고 필드를 남긴다
            RoomInvitationStatus status,
            boolean isDeleted
    ) {

    }
}
