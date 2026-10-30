package com.runiverse.running_service.application.notification.common;

import com.runiverse.running_service.application.common.port.out.LoadPlayerProfilesPort;
import com.runiverse.running_service.application.common.port.out.LoadRelationshipTypesPort;
import com.runiverse.running_service.application.common.port.out.PlayerProfile;
import com.runiverse.running_service.application.notification.port.out.InvitationSnapshot;
import com.runiverse.running_service.application.notification.port.out.LoadInvitationSnapshotsPort;
import com.runiverse.running_service.application.notification.port.out.UserNotificationRow;
import com.runiverse.running_service.application.notification.query.list.GetUserNotificationsResult;
import com.runiverse.running_service.application.user.port.out.GenerateViewUrlPort;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.notification.vo.FriendRequestAcceptedPayload;
import com.runiverse.running_service.domain.notification.vo.FriendRequestReceivedPayload;
import com.runiverse.running_service.domain.notification.vo.RunningInvitationReceivedPayload;
import com.runiverse.running_service.domain.notification.vo.UserNotificationPayload;
import com.runiverse.running_service.domain.relationship.vo.RelationshipType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

// 알림 행에 지금 상태(사람·관계·초대)를 붙여 항목으로 만든다 — 알림 목록과 스트림 전송이 함께 쓴다.
// 한 곳에서 만들어야 목록 항목과 스트림 이벤트의 모양이 어긋나지 않는다
@Slf4j
@Component
@RequiredArgsConstructor
public class UserNotificationItemAssembler {

    private static final String DELETED_NICKNAME = "탈퇴한 사용자";

    private final LoadPlayerProfilesPort loadPlayerProfilesPort;
    private final LoadRelationshipTypesPort loadRelationshipTypesPort;
    private final LoadInvitationSnapshotsPort loadInvitationSnapshotsPort;
    private final GenerateViewUrlPort generateViewUrlPort;

    // 사람·방을 한 번씩만 읽는다 — 항목마다 읽으면 N+1이다. 초대 행이 사라진 초대 알림은 뺀다
    public List<GetUserNotificationsResult.Item> assemble(UserId viewerId, List<UserNotificationRow> rows) {
        Set<UUID> actorIds = rows.stream()
                .map(row -> row.payload().actorId().value())
                .collect(Collectors.toSet());
        Set<Long> roomIds = rows.stream()
                .map(row -> roomIdOf(row.payload()))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<UUID, PlayerProfile> profiles = actorIds.isEmpty() ? Map.of() :
                loadPlayerProfilesPort.loadProfiles(actorIds);
        Map<UUID, RelationshipType> relationships =
                actorIds.isEmpty() ? Map.of() : loadRelationshipTypesPort.loadTypes(viewerId, actorIds);
        Map<Long, InvitationSnapshot> invitations =
                roomIds.isEmpty() ? Map.of() : loadInvitationSnapshotsPort.loadSnapshots(viewerId, roomIds);
        return rows.stream()
                .map(row -> toItem(row, profiles, relationships, invitations))
                .flatMap(Optional::stream)
                .toList();
    }

    private Optional<GetUserNotificationsResult.Item> toItem(UserNotificationRow row,
                                                             Map<UUID, PlayerProfile> profiles,
                                                             Map<UUID, RelationshipType> relationships,
                                                             Map<Long, InvitationSnapshot> invitations) {
        GetUserNotificationsResult.Invitation invitation = null;
        Long roomId = roomIdOf(row.payload());
        if (roomId != null) {
            InvitationSnapshot snapshot = invitations.get(roomId);
            if (snapshot == null) {
                // 초대는 받은 사람이 탈퇴할 때만 지워지는데 그때는 알림도 함께 지워진다 — 여기 오면 어긋난 데이터다
                log.warn("[알림] 목록 항목 건너뜀: 초대 행 없음 - notificationId={}, runningRoomId={}",
                        row.userNotificationId(), roomId);
                return Optional.empty();
            }
            invitation = new GetUserNotificationsResult.Invitation(
                    snapshot.runningRoomId(), snapshot.status(),
                    snapshot.scheduledStartAt(), snapshot.targetDistanceMeters());
        }
        return Optional.of(new GetUserNotificationsResult.Item(
                row.userNotificationId(), row.payload().type(), row.read(), row.createdAt(),
                toActor(row.payload().actorId().value(), profiles, relationships), invitation));
    }

    // 프로필이 없으면 탈퇴한 사람이다 — 관계 행도 함께 지워져 관계를 싣지 않는다
    private GetUserNotificationsResult.Actor toActor(UUID actorId,
                                                     Map<UUID, PlayerProfile> profiles,
                                                     Map<UUID, RelationshipType> relationships) {
        PlayerProfile profile = profiles.get(actorId);
        if (profile == null) {
            return new GetUserNotificationsResult.Actor(actorId, DELETED_NICKNAME, null, true, null);
        }
        String imageUrl = profile.profileImageKey() == null ? null :
                generateViewUrlPort.generate(profile.profileImageKey());
        // 관계 행은 처음 요청할 때 생긴다 — 행이 없으면 아무 관계도 아니다
        RelationshipType relationshipType = relationships.getOrDefault(actorId, RelationshipType.NONE);
        return new GetUserNotificationsResult.Actor(
                actorId, profile.nickname(), imageUrl, false, relationshipType);
    }

    // 종류가 늘면 sealed라 여기서 컴파일 에러가 난다 — 방을 가리키는 알림인지 빠뜨리지 않는다
    private static Long roomIdOf(UserNotificationPayload payload) {
        return switch (payload) {
            case FriendRequestReceivedPayload p -> null;
            case FriendRequestAcceptedPayload p -> null;
            case RunningInvitationReceivedPayload p -> p.runningRoomId().value();
        };
    }
}
