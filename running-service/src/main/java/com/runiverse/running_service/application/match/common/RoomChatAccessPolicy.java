package com.runiverse.running_service.application.match.common;

import com.runiverse.running_service.application.match.exception.RoomChatClosedException;
import com.runiverse.running_service.application.match.exception.RoomChatRoomNotFoundException;
import com.runiverse.running_service.application.running.port.out.LoadUserStatusPort;
import com.runiverse.running_service.application.running.port.out.UserStatusRow;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.running.room.vo.RunningRoomStatus;
import com.runiverse.running_service.domain.running.room.vo.RunningRoomType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

// 대기방 채팅을 쓸 수 있는가 — 보내기·목록·수정·삭제가 같은 판정을 써야 응답이 어긋나지 않는다.
// 판정 순서는 방 안인가(404) → 채팅이 열려 있나(409)다. 방 밖의 사람에게는 방 상태를 알려 주지 않는다.
// 방을 잠그지 않는다 — 채팅은 방 상태를 바꾸지 않아 지킬 불변식이 없고,
// 채팅마다 방 행을 잡으면 참가·이탈과 서로 기다린다
@Component
@RequiredArgsConstructor
public class RoomChatAccessPolicy {

    private final LoadUserStatusPort loadUserStatusPort;

    public void requireOpen(UserId userId, Long runningRoomId) {
        // 1. 이 방에 배정돼 있나 — 배정이 없거나, 다른 방에 배정됐거나, 솔로 방이면 셋 다 404다.
        //    배정은 유저당 하나라 "내 배정 방 = 요청한 방"이면 방 존재도 함께 확인된다
        UserStatusRow status = loadUserStatusPort.loadStatus(userId)
                .filter(row -> row.runningRoomId().equals(runningRoomId))
                .filter(row -> row.type() != RunningRoomType.SOLO)
                .orElseThrow(RoomChatRoomNotFoundException::new);
        // 2. 채팅이 열려 있나 — 모집 중·확정 후 대기만 된다. 이 동안의 활성 신청은 늘 JOINED라 따로 보지 않는다
        if (!isOpen(status.roomStatus())) {
            throw new RoomChatClosedException();
        }
    }

    private static boolean isOpen(RunningRoomStatus roomStatus) {
        return roomStatus == RunningRoomStatus.MATCHING || roomStatus == RunningRoomStatus.MATCHED;
    }
}
