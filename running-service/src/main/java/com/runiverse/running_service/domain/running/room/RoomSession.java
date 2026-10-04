package com.runiverse.running_service.domain.running.room;

import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.running.player.vo.RunningPlayerId;
import com.runiverse.running_service.domain.running.room.vo.LeaveCount;
import lombok.Getter;

@Getter
public class RoomSession {

    // 세션의 키는 유저다 — 같은 방에 다시 신청해도 행이 늘지 않는다(erd)
    private final UserId userId;
    // 지금 이 방에 들어와 있는 신청. 재배정되면 새 신청으로 갈린다
    private RunningPlayerId runningPlayerId;
    private LeaveCount leaveCount;
    private boolean connected;

    // private으로 설정함으로써 외부에서는 생성이 불가능 하다
    private RoomSession(UserId userId, RunningPlayerId runningPlayerId,
                        int leaveCount, boolean connected) {
        this.userId = userId;
        this.runningPlayerId = runningPlayerId;
        this.leaveCount = new LeaveCount(leaveCount);
        this.connected = connected;
    }

    // default 패키지로 내림으로써 같은 패키지인 room에서만 접근이 가능하다
    // 방 배정 시 — 처음 맺는 관계
    static RoomSession open(UserId userId, RunningPlayerId runningPlayerId) {
        return new RoomSession(userId, runningPlayerId, 0, true);
    }

    // DB 복원 — 방을 거쳐야만 들어온다
    static RoomSession from(SessionDraft draft) {
        return new RoomSession(draft.userId(), draft.runningPlayerId(),
                draft.leaveCount(), draft.connected());
    }

    // 방에서 나감 — 관계 row는 남기고 나간 이력만 새긴다
    void leave() {
        this.connected = false;
        this.leaveCount = leaveCount.increase();
    }

    // 완주로 자리를 비움 — 이탈이 아니므로 leave_count를 올리지 않는다
    void finish() {
        this.connected = false;
    }

    // 다시 들어옴 — 세션을 새로 만들지 않고 되살린다
    void rejoin() {
        this.connected = true;
    }

    // 새 신청으로 이 방에 다시 배정됨 — 행을 되살리고 신청만 갈아 끼운다
    void reassign(RunningPlayerId runningPlayerId) {
        this.runningPlayerId = runningPlayerId;
        this.connected = true;
    }

    public boolean isSameUser(UserId userId) {
        return this.userId.equals(userId);
    }

    public boolean isSamePlayer(RunningPlayerId runningPlayerId) {
        return this.runningPlayerId.equals(runningPlayerId);
    }
}
