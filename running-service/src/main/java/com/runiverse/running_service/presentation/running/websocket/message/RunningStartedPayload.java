package com.runiverse.running_service.presentation.running.websocket.message;

import com.runiverse.running_service.application.running.query.snapshot.GetRunningSnapshotResult;

import java.time.LocalDateTime;
import java.util.List;

// RUNNING_START의 ack. 상태가 걸린 요청의 ack 중 유일하게 data를 채운다 —
// 진입과 재연결에 똑같이 나가며, 이게 있어야 RUNNING_PROGRESS_UPDATED·
// RUNNING_COMBO_UPDATED가 userId만 싣는 설계가 성립한다(api-spec 5-C)
public record RunningStartedPayload(
        Long runningRoomId,
        LocalDateTime startedAt,
        Integer targetDistanceMeters,
        List<Player> players,
        // RUNNING_COMBO_UPDATED의 peers와 같은 모양이다 —
        // 모양을 맞춰야 클라가 한 벌의 코드로 스냅샷과 갱신을 다 그린다
        List<RunningComboUpdatedPayload.Peer> comboPeers
) {

    public record Player(
            String userId,
            String nickname,
            String profileImageUrl,
            int distanceMeters,
            Integer currentPaceSecondsPerKm,
            boolean paused
    ) {

    }

    public static RunningStartedPayload from(GetRunningSnapshotResult snapshot) {
        return new RunningStartedPayload(
                snapshot.runningRoomId(),
                snapshot.startedAt(),
                snapshot.targetDistanceMeters(),
                snapshot.players().stream()
                        .map(player -> new Player(
                                player.userId().toString(),
                                player.nickname(),
                                player.profileImageUrl(),
                                player.distanceMeters(),
                                player.currentPaceSecondsPerKm(),
                                player.paused()))
                        .toList(),
                // 갱신 통지가 쓰는 변환을 그대로 빌린다 — 두 벌로 나뉘면 모양이 갈라진다
                RunningComboUpdatedPayload.from(snapshot.comboPeers()).peers());
    }
}
