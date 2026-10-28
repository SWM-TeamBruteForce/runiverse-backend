package com.runiverse.running_service.application.match.port.out;

import com.runiverse.running_service.domain.running.room.RunningRoom;

public interface UpdateMatchRoomPort {

    // 합류로 바뀐 인원·평균 페이스·세션을 반영한다.
    // 초대 방이면 초대 행도 함께 맞춘다 — 받는 사람의 행이 보낸 사람별로 여러 개라
    // 하나만 저장되면 "같은 받는 사람의 행은 늘 같은 상태"가 깨진다
    void update(RunningRoom room);
}
