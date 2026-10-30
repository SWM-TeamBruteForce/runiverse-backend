package com.runiverse.running_service.application.match.common;

import com.runiverse.running_service.application.device.command.push.PushRequestedEvent;
import com.runiverse.running_service.application.device.port.out.PushMessage;
import com.runiverse.running_service.application.device.port.out.PushType;
import com.runiverse.running_service.application.match.port.out.RoomInfo;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.running.room.RoomInvitation;
import com.runiverse.running_service.domain.running.room.RoomSession;
import com.runiverse.running_service.domain.running.room.RunningRoom;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;

// 매칭 확정·초대 수락 푸시 — 시작하면 의미가 없어 알림함에 쌓지 않고 시작 시각에 만료된다.
// 커밋 뒤에 보내므로 방을 바꾼 트랜잭션 안에서 부른다
@Slf4j
@Component
@RequiredArgsConstructor
public class MatchPushRequester {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    private final ApplicationEventPublisher eventPublisher;

    // 방에 남은 사람 전원이 받는다. 인원은 받는 사람 본인을 포함한다
    public void matchConfirmed(RunningRoom room) {
        List<UserId> recipients = room.getSessions().stream()
                .filter(RoomSession::isConnected)
                .map(RoomSession::getUserId)
                .toList();
        String startTime = room.getStartAt().format(TIME_FORMAT);
        Map<String, String> data = Map.of("runningRoomId", String.valueOf(roomIdOf(room)));
        PushMessage message = recipients.size() > 1
                ? new PushMessage(PushType.MATCH_CONFIRMED, "매칭이 완료됐어요",
                        "%s 러닝에 %d명이 모였어요".formatted(startTime, recipients.size()), data, room.getStartAt())
                : new PushMessage(PushType.MATCH_CONFIRMED, "혼자 달리게 됐어요",
                        "%s에 혼자 달려요. 지금 나가도 다시 신청할 수 있어요".formatted(startTime), data, room.getStartAt());
        recipients.forEach(userId -> eventPublisher.publishEvent(new PushRequestedEvent(userId.value(), message)));
    }

    // 들어온 사람을 초대했고 아직 방에 있는 사람만 받는다.
    // 닉네임은 방 정보 조립이 이미 읽은 값을 쓴다 — 여기서 다시 읽으면 그 조회가 실패할 때 수락까지 롤백된다
    public void invitationAccepted(RunningRoom room, UserId inviteeId, RoomInfo roomInfo) {
        List<UserId> recipients = room.getInvitations().stream()
                .filter(invitation -> invitation.isFor(inviteeId))
                .map(RoomInvitation::getInviterId)
                .filter(room::isInRoom)
                .toList();
        if (recipients.isEmpty()) {
            return;
        }
        // 탈퇴했으면 닉네임 자리가 표시용 문구라 보내지 않는다. 던지면 수락까지 롤백된다
        Optional<RoomInfo.RoomPlayer> invitee = roomInfo.players().stream()
                .filter(player -> player.userId().equals(inviteeId.value()) && !player.isDeleted())
                .findFirst();
        if (invitee.isEmpty()) {
            log.warn("[매칭] 초대 수락 푸시 건너뜀: 수락한 사람 프로필 없음 - roomId={}, inviteeId={}",
                    roomIdOf(room), inviteeId.value());
            return;
        }
        PushMessage message = new PushMessage(PushType.INVITATION_ACCEPTED, "초대를 수락했어요",
                "%s님이 %s 러닝에 들어왔어요".formatted(invitee.get().nickname(), room.getStartAt().format(TIME_FORMAT)),
                Map.of("actorId", inviteeId.value().toString(), "runningRoomId", String.valueOf(roomIdOf(room))),
                room.getStartAt());
        recipients.forEach(userId -> eventPublisher.publishEvent(new PushRequestedEvent(userId.value(), message)));
    }

    private static Long roomIdOf(RunningRoom room) {
        return room.getRunningRoomId()
                .orElseThrow(() -> new IllegalStateException("저장되지 않은 방은 알릴 수 없다"))
                .value();
    }
}
