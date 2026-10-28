package com.runiverse.running_service.application.notification.port.out;

import com.runiverse.running_service.domain.running.room.vo.RoomInvitationStatus;

import java.time.LocalDateTime;

// 받은 초대의 지금 상태와 그 방 — 초대 알림의 invitation에 그대로 실린다.
// 같은 방에서 여러 사람에게 받은 초대 행은 함께 바뀌므로 방마다 하나로 본다
public record InvitationSnapshot(
        Long runningRoomId,
        RoomInvitationStatus status,
        LocalDateTime scheduledStartAt,
        Integer targetDistanceMeters
) {

}
