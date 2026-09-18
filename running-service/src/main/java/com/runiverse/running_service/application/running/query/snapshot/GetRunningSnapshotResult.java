package com.runiverse.running_service.application.running.query.snapshot;

import com.runiverse.running_service.application.running.port.out.RunningComboPeer;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

// RUNNING_STARTED가 나르는 진입·재연결 화면 복구용 스냅샷(api-spec 5-C).
// RoomInfo를 재사용하지 않는다 — 그쪽은 매칭 대기방 구조라 진행이라는 개념이 없고,
// 러닝 중에는 closeAt·teamAveragePace가 의미를 잃는다
public record GetRunningSnapshotResult(
        Long runningRoomId,
        // 방의 start_at이다. 참가자가 실제로 RUNNING이 된 시각을 쓰면
        // 같은 방에서 사람마다 경과 시간이 달라진다
        LocalDateTime startedAt,
        // 목표 없는 솔로 방은 null
        Integer targetDistanceMeters,
        List<Player> players,
        // 받는 사람이 낀 관계만. 겹치는 상대가 없으면 빈 목록이다
        List<RunningComboPeer> comboPeers
) {

    // RoomInfo의 프로필 + RUNNING_PROGRESS_UPDATED의 진행을 합친 모양이다 —
    // 클라는 이 스냅샷으로 채우고 갱신분으로 덮는 한 쌍으로 다룬다
    public record Player(
            UUID userId,
            String nickname,
            String profileImageUrl,
            int distanceMeters,
            Integer currentPaceSecondsPerKm,
            boolean paused
    ) {

    }
}
