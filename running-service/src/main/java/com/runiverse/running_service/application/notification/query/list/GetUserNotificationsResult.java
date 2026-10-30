package com.runiverse.running_service.application.notification.query.list;

import com.runiverse.running_service.domain.notification.vo.UserNotificationType;
import com.runiverse.running_service.domain.relationship.vo.RelationshipType;
import com.runiverse.running_service.domain.running.room.vo.RoomInvitationStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

// 마지막 페이지면 nextCursor는 null. unreadCount는 페이지와 무관하게 보관 기간 안의 안 읽은 알림 전체 수다
public record GetUserNotificationsResult(List<Item> items, String nextCursor, long unreadCount) {

    public record Item(
            Long notificationId,
            UserNotificationType type,
            boolean read,
            LocalDateTime createdAt,
            Actor actor,
            Invitation invitation               // RUNNING_INVITATION_RECEIVED만 있고 그 밖에는 null
    ) {

    }

    // 알림을 일으킨 사람 — 탈퇴했으면 공통 탈퇴 유저 형식이고 관계는 싣지 않는다
    public record Actor(
            UUID userId,
            String nickname,                    // 탈퇴했으면 "탈퇴한 사용자"
            String profileImageUrl,             // 사진이 없거나 탈퇴했으면 null
            boolean deleted,
            RelationshipType relationshipType   // 조회자 기준 지금 관계. 탈퇴했으면 null
    ) {

    }

    // 받은 초대의 지금 상태와 그 방
    public record Invitation(
            Long runningRoomId,
            RoomInvitationStatus status,
            LocalDateTime scheduledStartAt,
            Integer targetDistanceMeters
    ) {

    }
}
