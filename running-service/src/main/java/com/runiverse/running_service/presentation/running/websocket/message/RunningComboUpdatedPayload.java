package com.runiverse.running_service.presentation.running.websocket.message;

import com.runiverse.running_service.application.running.port.out.RunningComboPeer;

import java.util.List;

// 받는 사람의 콤보 상태 전체다. 끊긴 상대는 목록에서 빠지므로
// 끊김을 알리는 별도 이벤트가 없고, 클라는 이 목록으로 화면을 갈아끼운다
public record RunningComboUpdatedPayload(List<Peer> peers) {

    public record Peer(
            String userId,
            // 양수면 상대가 앞, 음수면 뒤
            int gapMeters,
            int comboCount,
            int maxComboCount
    ) {

    }

    public static RunningComboUpdatedPayload from(List<RunningComboPeer> peers) {
        return new RunningComboUpdatedPayload(peers.stream()
                .map(peer -> new Peer(
                        peer.userId().toString(),
                        peer.gapMeters(),
                        peer.comboCount(),
                        peer.maxComboCount()))
                .toList());
    }
}
