package com.runiverse.running_service.application.match.command.invitedecline;

import com.runiverse.running_service.application.match.exception.InvitationNotFoundException;
import com.runiverse.running_service.application.match.exception.RunningInvitationNotPendingException;
import com.runiverse.running_service.application.match.port.in.DeclineInvitationUsecase;
import com.runiverse.running_service.application.match.port.out.LockMatchRoomPort;
import com.runiverse.running_service.application.match.port.out.UpdateMatchRoomPort;
import com.runiverse.running_service.application.match.common.MatchRoomChangedEvent;
import com.runiverse.running_service.application.match.common.RoomInfoAssembler;
import com.runiverse.running_service.application.match.port.out.MatchStreamEvent;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.running.room.RunningRoom;
import com.runiverse.running_service.domain.running.room.vo.RoomInvitationStatus;
import com.runiverse.running_service.domain.running.room.vo.RunningRoomId;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class DeclineInvitationHandler implements DeclineInvitationUsecase {

    private final LockMatchRoomPort lockMatchRoomPort;
    private final UpdateMatchRoomPort updateMatchRoomPort;
    private final RoomInfoAssembler roomInfoAssembler;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    public void handle(DeclineInvitationCommand command) {
        UserId inviteeId = new UserId(command.userId());
        // 1. 방을 잠그고 본다 — 같은 순간의 수락·다시 초대와 엇갈리지 않게 한다.
        //    방이 없거나 이 방에서 나에게 온 초대가 없으면 둘 다 404다
        RunningRoom room = lockMatchRoomPort.lockById(new RunningRoomId(command.runningRoomId()))
                .filter(locked -> locked.invitationStatusOf(inviteeId).isPresent())
                .orElseThrow(InvitationNotFoundException::new);
        // 2. 대기 중인 초대만 거절한다 — 이미 응답했거나 만료됐으면 409.
        //    마감은 따로 보지 않는다 — 거절은 자리를 잡지 않아 마감과 무관하다
        if (room.invitationStatusOf(inviteeId).orElseThrow() != RoomInvitationStatus.PENDING) {
            throw new RunningInvitationNotPendingException();
        }
        // 3. 보낸 사람별 행을 모두 거절로 바꾼다 — 지우지 않는다(현황에서는 빠지지만 다시 초대할 때 보낸 횟수를 이어서 센다)
        room.declineInvitation(inviteeId);
        updateMatchRoomPort.update(room);
        // 4. 방 참가자에게 초대 현황을 알린다
        eventPublisher.publishEvent(new MatchRoomChangedEvent(
                MatchStreamEvent.updated(roomInfoAssembler.assemble(room))));
    }
}
