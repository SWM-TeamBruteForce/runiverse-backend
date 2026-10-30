package com.runiverse.running_service.application.match.command.invitesend;

import com.runiverse.running_service.application.common.port.out.SendUserNotificationPort;
import com.runiverse.running_service.application.match.common.InviteProperties;
import com.runiverse.running_service.application.match.common.InviteStartPolicy;
import com.runiverse.running_service.application.match.common.MatchRoomChangedEvent;
import com.runiverse.running_service.application.match.common.RoomInfoAssembler;
import com.runiverse.running_service.application.match.exception.InviteCountExceededException;
import com.runiverse.running_service.application.match.exception.InviteRoomNotFoundException;
import com.runiverse.running_service.application.match.exception.InviteeAlreadyInRoomException;
import com.runiverse.running_service.application.match.exception.InviteeInMatchingException;
import com.runiverse.running_service.application.match.exception.InviteeRunningException;
import com.runiverse.running_service.application.match.exception.NotFriendException;
import com.runiverse.running_service.application.match.exception.RunningInvitationClosedException;
import com.runiverse.running_service.application.match.port.in.SendInvitationUsecase;
import com.runiverse.running_service.application.match.port.out.CheckFriendPort;
import com.runiverse.running_service.application.match.port.out.LockMatchRoomPort;
import com.runiverse.running_service.application.match.port.out.MatchStreamEvent;
import com.runiverse.running_service.application.match.port.out.UpdateMatchRoomPort;
import com.runiverse.running_service.application.running.port.out.LoadUserStatusPort;
import com.runiverse.running_service.application.running.port.out.UserStatusRow;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.notification.vo.RunningInvitationReceivedPayload;
import com.runiverse.running_service.domain.running.room.RunningRoom;
import com.runiverse.running_service.domain.running.room.vo.RunningRoomId;
import com.runiverse.running_service.domain.running.room.vo.RunningRoomStatus;
import com.runiverse.running_service.domain.running.room.vo.RunningRoomType;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

// 한 번에 한 명이다 — 못 보내는 경우는 건너뛰지 않고 그 사람이 왜 안 되는지를 409로 알린다.
// 아래 번호 순서로 보고, 먼저 걸린 이유 하나만 알린다
@Service
@RequiredArgsConstructor
@Transactional
public class SendInvitationHandler implements SendInvitationUsecase {

    private final LockMatchRoomPort lockMatchRoomPort;
    private final UpdateMatchRoomPort updateMatchRoomPort;
    private final CheckFriendPort checkFriendPort;
    private final LoadUserStatusPort loadUserStatusPort;
    private final InviteStartPolicy inviteStartPolicy;
    private final InviteProperties inviteProperties;
    private final RoomInfoAssembler roomInfoAssembler;
    private final SendUserNotificationPort sendUserNotificationPort;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    public void handle(SendInvitationCommand command) {
        UserId inviterId = new UserId(command.userId());
        UserId inviteeId = new UserId(command.inviteeId());
        // 1. 방을 잠그고 본다 — 없거나, 초대 방이 아니거나, 호출자가 지금 이 방에 없으면 셋 다 404다.
        //    애그리거트도 같은 규칙을 지키지만 그쪽 예외는 500이라 여기서 먼저 거른다
        RunningRoom room = lockMatchRoomPort.lockById(new RunningRoomId(command.runningRoomId()))
                .filter(locked -> locked.getType() == RunningRoomType.INVITE)
                .filter(locked -> locked.isInRoom(inviterId))
                .orElseThrow(InviteRoomNotFoundException::new);
        // 2. 모집 중이고 마감 전인가 — 경계는 수락과 같아야 해 정책이 함께 판정한다
        if (!inviteStartPolicy.isRecruiting(room, LocalDateTime.now())) {
            throw new RunningInvitationClosedException();
        }
        // 3. 친구인가 — 자기 자신도 친구가 아니라 여기서 걸린다(도메인의 자기 초대 예외는 500이다)
        if (!checkFriendPort.isFriend(inviterId, inviteeId)) {
            throw new NotFriendException();
        }
        // 4. 이미 이 방에 있나 — 아래 활성 신청 판정보다 먼저 봐야 "매칭 중"이 아니라 정확한 이유가 나간다
        if (room.isInRoom(inviteeId)) {
            throw new InviteeAlreadyInRoomException();
        }
        // 5. 다른 방의 활성 신청 — 이 방 애그리거트는 볼 수 없어 따로 읽는다
        loadUserStatusPort.loadStatus(inviteeId).ifPresent(SendInvitationHandler::rejectBusy);
        // 6. 이 친구에게 보낸 횟수가 상한에 닿았나 — 보낸 사람마다 센다
        int maxInviteCount = inviteProperties.maxInviteCount();
        if (room.hasReachedInviteLimit(inviterId, inviteeId, maxInviteCount)) {
            throw new InviteCountExceededException();
        }
        room.invite(inviterId, inviteeId, maxInviteCount);
        updateMatchRoomPort.update(room);
        // 받은 사람에게 초대가 왔다고 알린다 — 다시 초대도 새 사건이라 새로 쌓는다.
        // 같은 트랜잭션이라 초대만 남거나 알림만 남지 않는다
        sendUserNotificationPort.send(inviteeId,
                new RunningInvitationReceivedPayload(inviterId, new RunningRoomId(command.runningRoomId())));
        // 방 참가자에게 초대 현황을 알린다 — 조립은 트랜잭션 안에서 한다(자기 쓰기를 본다)
        eventPublisher.publishEvent(new MatchRoomChangedEvent(
                MatchStreamEvent.updated(roomInfoAssembler.assemble(room))));
    }

    // 솔로는 모집 단계가 없어 시작 전이어도 러닝으로 본다. 시작된 방도 러닝이고, 그 밖은 모집·확정 후 대기다
    private static void rejectBusy(UserStatusRow status) {
        if (status.type() == RunningRoomType.SOLO || status.roomStatus() == RunningRoomStatus.STARTED) {
            throw new InviteeRunningException();
        }
        throw new InviteeInMatchingException();
    }
}
