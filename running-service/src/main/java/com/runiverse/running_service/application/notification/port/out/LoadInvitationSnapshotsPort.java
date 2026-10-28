package com.runiverse.running_service.application.notification.port.out;

import com.runiverse.running_service.domain.common.vo.UserId;

import java.util.Collection;
import java.util.Map;

public interface LoadInvitationSnapshotsPort {

    // 받은 사람이 이 방들에서 받은 초대의 지금 상태 — 방 ID로 찾는다.
    // 초대 행이 없는 방은 결과에서 빠진다. 호출자가 "없으면 그 알림을 뺀다"로 판정한다
    Map<Long, InvitationSnapshot> loadSnapshots(UserId inviteeId, Collection<Long> runningRoomIds);
}
