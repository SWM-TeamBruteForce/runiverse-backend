package com.runiverse.running_service.application.running.port.out;

import com.runiverse.running_service.domain.running.room.vo.RunningRoomStatus;
import com.runiverse.running_service.domain.running.room.vo.RunningRoomType;

import java.time.LocalDateTime;

// 상태 판정에 필요한 값만 추린 한 행 — 애그리거트를 통째로 올리지 않는다.
// 활성 신청은 유저당 하나라 조회 결과도 최대 한 행이다
public record UserStatusRow(
        Long runningRoomId,
        RunningRoomType type,
        RunningRoomStatus roomStatus,
        // 둘 다 방 쪽이 정본이다 — running_players.target_distance는 NOT NULL이라
        // 목표 없는 솔로에도 값이 들어가 null을 구분할 수 없다
        LocalDateTime scheduledStartAt,
        Integer targetDistanceMeters
) {

}
