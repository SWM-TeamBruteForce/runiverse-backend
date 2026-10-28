package com.runiverse.running_service.domain.running.room.vo;

import com.runiverse.running_service.domain.running.room.exception.InvalidInviteCountException;

// 한 보낸 사람이 한 받는 사람에게 보낸 횟수 — 행은 보낼 때 생기므로 0이 없다
public record InviteCount(int value) {

    private static final int MIN = 1;

    public InviteCount {
        if (value < MIN) {
            throw new InvalidInviteCountException();
        }
    }

    public static InviteCount first() {
        return new InviteCount(MIN);
    }

    // 다시 보낼 때마다 하나씩 — 거절·나감 뒤에도 이어서 센다
    public InviteCount increase() {
        return new InviteCount(value + 1);
    }

    // 상한은 운영 설정이라 밖에서 받는다
    public boolean hasReached(int max) {
        return value >= max;
    }
}
