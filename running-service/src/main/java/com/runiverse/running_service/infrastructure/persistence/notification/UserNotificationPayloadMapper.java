package com.runiverse.running_service.infrastructure.persistence.notification;

import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.notification.vo.FriendRequestAcceptedPayload;
import com.runiverse.running_service.domain.notification.vo.FriendRequestReceivedPayload;
import com.runiverse.running_service.domain.notification.vo.RunningInvitationReceivedPayload;
import com.runiverse.running_service.domain.notification.vo.UserNotificationPayload;
import com.runiverse.running_service.domain.notification.vo.UserNotificationType;
import com.runiverse.running_service.domain.running.room.vo.RunningRoomId;

import java.util.Map;
import java.util.UUID;

// 도메인 payload ↔ jsonb의 키 이름을 정하는 유일한 곳이다.
// 키는 저장된 약속이다 — 보관 기간 동안 옛 행이 남으므로 이름을 바꾸지 않는다
final class UserNotificationPayloadMapper {

    static final String REQUEST_USER_ID = "request_user_id";
    static final String ACCEPT_USER_ID = "accept_user_id";
    static final String RUNNING_ROOM_ID = "running_room_id";

    private UserNotificationPayloadMapper() {
    }

    // 종류가 늘면 sealed라 여기서 컴파일 에러가 난다 — 키를 빠뜨린 채 저장되지 않는다
    static Map<String, Object> toJson(UserNotificationPayload payload) {
        return switch (payload) {
            case FriendRequestReceivedPayload p -> Map.of(REQUEST_USER_ID, p.requestUserId().value().toString());
            case FriendRequestAcceptedPayload p -> Map.of(ACCEPT_USER_ID, p.acceptUserId().value().toString());
            // 방 ID는 숫자로 둔다 — 초대 상태를 찾는 running_room_invitations의 bigint와 같은 값이다
            case RunningInvitationReceivedPayload p -> Map.of(
                    REQUEST_USER_ID, p.requestUserId().value().toString(),
                    RUNNING_ROOM_ID, p.runningRoomId().value());
        };
    }

    // 저장할 때와 같은 생성자를 거친다 — 키가 빠졌거나 값이 틀린 행은 여기서 예외로 걸러진다.
    // 어떤 예외든 "되살리지 못한 행"이라 어댑터가 잡아 그 행만 뺀다
    static UserNotificationPayload fromJson(UserNotificationType type, Map<String, Object> json) {
        return switch (type) {
            case FRIEND_REQUEST_RECEIVED -> new FriendRequestReceivedPayload(userIdOf(json, REQUEST_USER_ID));
            case FRIEND_REQUEST_ACCEPTED -> new FriendRequestAcceptedPayload(userIdOf(json, ACCEPT_USER_ID));
            case RUNNING_INVITATION_RECEIVED -> new RunningInvitationReceivedPayload(
                    userIdOf(json, REQUEST_USER_ID), roomIdOf(json, RUNNING_ROOM_ID));
        };
    }

    // 없는 키는 null로 넘겨 payload 생성자가 도메인 예외로 막게 한다
    private static UserId userIdOf(Map<String, Object> json, String key) {
        Object value = json.get(key);
        return value == null ? null : new UserId(UUID.fromString(value.toString()));
    }

    // jsonb 숫자는 크기에 따라 Integer·Long으로 읽힌다 — Number로 받아 맞춘다
    private static RunningRoomId roomIdOf(Map<String, Object> json, String key) {
        Object value = json.get(key);
        return value == null ? null : new RunningRoomId(((Number) value).longValue());
    }
}
