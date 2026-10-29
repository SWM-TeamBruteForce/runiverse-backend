package com.runiverse.running_service.presentation.notification.response;

import com.runiverse.running_service.application.notification.query.list.GetUserNotificationsResult;
import com.runiverse.running_service.domain.notification.vo.UserNotificationType;
import com.runiverse.running_service.domain.relationship.vo.RelationshipType;
import com.runiverse.running_service.domain.running.room.vo.RoomInvitationStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

// 마지막 페이지면 nextCursor는 null. unreadCount는 페이지와 무관한 보관 기간 안의 전체 안 읽은 수다 — 앱 진입 때 배지를 여기서 그린다
public record UserNotificationsResponse(List<ItemResponse> items, String nextCursor, long unreadCount) {

    public static UserNotificationsResponse from(GetUserNotificationsResult result) {
        return new UserNotificationsResponse(
                result.items().stream().map(ItemResponse::from).toList(),
                result.nextCursor(),
                result.unreadCount());
    }

    public record ItemResponse(
            Long notificationId,
            UserNotificationType type,
            boolean isRead,
            LocalDateTime createdAt,
            ActorResponse actor,
            InvitationResponse invitation       // RUNNING_INVITED만 있고 그 밖에는 null
    ) {

        static ItemResponse from(GetUserNotificationsResult.Item item) {
            return new ItemResponse(
                    item.notificationId(),
                    item.type(),
                    item.read(),
                    item.createdAt(),
                    ActorResponse.from(item.actor()),
                    item.invitation() == null ? null : InvitationResponse.from(item.invitation()));
        }
    }

    // 탈퇴했으면 닉네임 "탈퇴한 사용자"·사진 null·isDeleted true이고 relationshipType도 null이다
    public record ActorResponse(
            UUID userId,
            String nickname,
            String profileImageUrl,
            boolean isDeleted,
            RelationshipType relationshipType   // 지금 나와 이 사람의 관계 — 모든 종류에 있다
    ) {

        static ActorResponse from(GetUserNotificationsResult.Actor actor) {
            return new ActorResponse(
                    actor.userId(),
                    actor.nickname(),
                    actor.profileImageUrl(),
                    actor.deleted(),
                    actor.relationshipType());
        }
    }

    // 받은 초대의 지금 상태 — PENDING이면 수락·거절 버튼을 그린다
    public record InvitationResponse(
            Long runningRoomId,
            RoomInvitationStatus status,
            LocalDateTime scheduledStartAt,
            Integer targetDistanceMeters
    ) {

        static InvitationResponse from(GetUserNotificationsResult.Invitation invitation) {
            return new InvitationResponse(
                    invitation.runningRoomId(),
                    invitation.status(),
                    invitation.scheduledStartAt(),
                    invitation.targetDistanceMeters());
        }
    }
}
