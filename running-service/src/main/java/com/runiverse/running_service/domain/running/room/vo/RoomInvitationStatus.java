package com.runiverse.running_service.domain.running.room.vo;

import com.runiverse.running_service.domain.running.room.exception.InvalidInvitationStatusTransitionException;

import java.util.Set;

// 받는 사람 기준의 상태 — 같은 방의 같은 받는 사람 행은 보낸 사람이 달라도 늘 같은 값이다
public enum RoomInvitationStatus {
    PENDING,    // 응답 전
    ACCEPTED,   // 수락해 지금 방에 있다 — 나가면 LEFT로 바뀐다
    DECLINED,   // 거절 — 현황에서는 빠지지만 다시 초대할 때 보낸 횟수를 이어 세려고 지우지 않는다
    LEFT,       // 수락했다가 방을 나갔다 — 거절과 같게 다시 초대할 수 있지만 이후 정책이 갈릴 수 있어 나눈다
    EXPIRED;    // 모집 마감·방 취소로 끝났다

    // 애그리거트가 상태를 바꾸기 전에 호출 -> 위반은 여기서 잡는다
    public RoomInvitationStatus transitionTo(RoomInvitationStatus next) {
        if (!allowedNext().contains(next)) {
            throw new InvalidInvitationStatusTransitionException();
        }
        return next;
    }

    private Set<RoomInvitationStatus> allowedNext() {
        return switch (this) {
            case PENDING -> Set.of(ACCEPTED, DECLINED, EXPIRED);
            case ACCEPTED -> Set.of(LEFT);
            // 거절했거나 나간 사람은 다시 초대로 대기가 된다
            case DECLINED, LEFT -> Set.of(PENDING);
            // 마감 뒤에는 초대할 수 없다 — 되살릴 일이 없다
            case EXPIRED -> Set.of();
        };
    }
}
