package com.runiverse.running_service.application.running.port.out;

import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.running.player.RunningPlayer;
import com.runiverse.running_service.domain.running.room.vo.RunningRoomId;

import java.util.List;
import java.util.Optional;

public interface LockRunningPlayerPort {

    // 취소와 시작이 같은 신청 행을 고친다 — 잠그지 않으면 낡은 스냅샷으로 deleted_at을 덮어써
    // 나간 사용자가 활성 신청으로 되살아난다. 읽는 순간 잠가야 상대 커밋을 보고 판단한다
    Optional<RunningPlayer> lockActive(UserId userId);

    // 방의 활성 참가자를 user_id 순으로 한 번에 잠근다 — 방보다 먼저 잡아야 시작·취소와 순서가 맞는다.
    // 방 행은 건드리지 않는다: 먼저 읽어 두면 이어지는 잠금 조회가 영속성 컨텍스트의 낡은 값을 돌려준다
    List<RunningPlayer> lockActiveInRoom(RunningRoomId runningRoomId);

    // deleted_at과 무관하게 이 방의 참가자를 잠근다 — 이미 끝난 참가자도 찾아야 RUNNING_FINISH가 멱등이 된다
    Optional<RunningPlayer> lockInRoom(RunningRoomId runningRoomId, UserId userId);
}
