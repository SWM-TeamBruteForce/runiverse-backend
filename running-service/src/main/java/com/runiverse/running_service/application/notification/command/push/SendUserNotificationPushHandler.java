package com.runiverse.running_service.application.notification.command.push;

import com.runiverse.running_service.application.device.command.push.SendPushCommand;
import com.runiverse.running_service.application.device.port.in.SendPushUsecase;
import com.runiverse.running_service.application.device.port.out.PushMessage;
import com.runiverse.running_service.application.device.port.out.PushType;
import com.runiverse.running_service.application.match.common.InviteStartPolicy;
import com.runiverse.running_service.application.notification.common.UserNotificationItemReader;
import com.runiverse.running_service.application.notification.port.in.SendUserNotificationPushUsecase;
import com.runiverse.running_service.application.notification.query.list.GetUserNotificationsResult;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.running.room.vo.RoomInvitationStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Optional;

// 쌓인 알림을 받는 사람의 폰으로도 보낸다 — 앱을 열기 전에도 알게 한다.
// 트랜잭션을 걸지 않는다: 읽기는 리더가, 발송은 발송 유스케이스가 각자 연다
@Slf4j
@Service
@RequiredArgsConstructor
public class SendUserNotificationPushHandler implements SendUserNotificationPushUsecase {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    private final UserNotificationItemReader userNotificationItemReader;
    private final InviteStartPolicy inviteStartPolicy;
    private final SendPushUsecase sendPushUsecase;

    @Override
    public void handle(SendUserNotificationPushCommand command) {
        UserId userId = new UserId(command.userId());
        Optional<GetUserNotificationsResult.Item> found =
                userNotificationItemReader.read(userId, command.notificationId());
        if (found.isEmpty()) {
            // 깨진 행·초대 행 없음은 어댑터·조립기가 원인을 남겼다
            log.warn("[알림] 기기 푸시 건너뜀: 알림 없음 - targetUserId={}, notificationId={}",
                    userId.value(), command.notificationId());
            return;
        }
        GetUserNotificationsResult.Item item = found.get();
        if (item.actor().deleted()) {
            log.info("[알림] 기기 푸시 건너뜀: 보낸 사람 탈퇴 - targetUserId={}, notificationId={}",
                    userId.value(), command.notificationId());
            return;
        }
        // 그새 수락·거절됐거나 모집이 끝났으면 알릴 것이 없다
        if (item.invitation() != null && item.invitation().status() != RoomInvitationStatus.PENDING) {
            log.info("[알림] 기기 푸시 건너뜀: 대기 중인 초대 아님 - targetUserId={}, notificationId={}, status={}",
                    userId.value(), command.notificationId(), item.invitation().status());
            return;
        }
        sendPushUsecase.handle(new SendPushCommand(userId.value(), toMessage(item)));
    }

    private PushMessage toMessage(GetUserNotificationsResult.Item item) {
        String nickname = item.actor().nickname();
        String actorId = item.actor().userId().toString();
        String notificationId = String.valueOf(item.notificationId());
        return switch (item.type()) {
            case FRIEND_REQUESTED -> new PushMessage(PushType.FRIEND_REQUESTED,
                    "친구 요청이 왔어요", "%s님이 친구 요청을 보냈어요".formatted(nickname),
                    Map.of("actorId", actorId, "notificationId", notificationId), null);
            case FRIEND_ACCEPTED -> new PushMessage(PushType.FRIEND_ACCEPTED,
                    "새 친구가 생겼어요", "%s님과 친구가 됐어요".formatted(nickname),
                    Map.of("actorId", actorId, "notificationId", notificationId), null);
            // 모집이 끝나면 수락할 수 없어 그 뒤에 켜진 폰은 받지 않는다
            case RUNNING_INVITED -> {
                GetUserNotificationsResult.Invitation invitation = item.invitation();
                yield new PushMessage(PushType.RUNNING_INVITED,
                        "러닝 초대가 왔어요",
                        "%s님이 %s 러닝에 초대했어요".formatted(
                                nickname, invitation.scheduledStartAt().format(TIME_FORMAT)),
                        Map.of("actorId", actorId, "notificationId", notificationId,
                                "runningRoomId", String.valueOf(invitation.runningRoomId())),
                        inviteStartPolicy.closeAt(invitation.scheduledStartAt()));
            }
        };
    }
}
