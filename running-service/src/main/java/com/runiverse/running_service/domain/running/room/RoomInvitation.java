package com.runiverse.running_service.domain.running.room;

import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.running.room.exception.SelfInvitationException;
import com.runiverse.running_service.domain.running.room.vo.InviteCount;
import com.runiverse.running_service.domain.running.room.vo.RoomInvitationStatus;
import lombok.Getter;

@Getter
public class RoomInvitation {

    // 키는 (보낸 사람, 받는 사람)이다 — 같은 사람이 다시 보내면 행이 늘지 않고 횟수만 오른다
    private final UserId inviterId;
    private final UserId inviteeId;
    // 받는 사람 기준의 상태 — 같은 받는 사람의 다른 행과 늘 같게 방이 맞춘다
    private RoomInvitationStatus status;
    private InviteCount inviteCount;

    private RoomInvitation(UserId inviterId, UserId inviteeId,
                           RoomInvitationStatus status, InviteCount inviteCount) {
        // DB CHECK(inviter_id <> invitee_id)와 같은 규칙 — 저장 전에 잡는다
        if (inviterId.equals(inviteeId)) {
            throw new SelfInvitationException();
        }
        this.inviterId = inviterId;
        this.inviteeId = inviteeId;
        this.status = status;
        this.inviteCount = inviteCount;
    }

    // 이 보낸 사람이 처음 보냄 — 상태는 방이 받는 사람을 대기로 맞춘 뒤라 늘 PENDING이다
    static RoomInvitation open(UserId inviterId, UserId inviteeId) {
        return new RoomInvitation(inviterId, inviteeId,
                RoomInvitationStatus.PENDING, InviteCount.first());
    }

    // DB 복원 — 방을 거쳐야만 들어온다
    static RoomInvitation from(InvitationDraft draft) {
        return new RoomInvitation(draft.inviterId(), draft.inviteeId(),
                draft.status(), new InviteCount(draft.inviteCount()));
    }

    // 같은 사람이 다시 보냄 — 상태는 방이 따로 맞춘다
    void resend() {
        this.inviteCount = inviteCount.increase();
    }

    void changeStatus(RoomInvitationStatus next) {
        this.status = status.transitionTo(next);
    }

    public boolean isFor(UserId inviteeId) {
        return this.inviteeId.equals(inviteeId);
    }

    public boolean isSentBy(UserId inviterId) {
        return this.inviterId.equals(inviterId);
    }

    public boolean isPending() {
        return status == RoomInvitationStatus.PENDING;
    }
}
