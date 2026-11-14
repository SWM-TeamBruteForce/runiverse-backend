package com.runiverse.running_service.application.match.port.out;

import java.util.Optional;

public interface LoadRoomChatPort {

    // 고치거나 지울 메시지를 잠가 읽는다 — 연달아 온 두 수정이 같은 직전 내용을 읽으면 이력 하나가 빠진다.
    // 방까지 조건으로 건다 — 다른 방의 메시지는 없는 것과 같다. 지운 메시지도 돌려주고, 거르는 건 유스케이스의 몫이다
    Optional<LockedRoomChat> loadForUpdate(Long runningRoomId, Long chatId);
}
