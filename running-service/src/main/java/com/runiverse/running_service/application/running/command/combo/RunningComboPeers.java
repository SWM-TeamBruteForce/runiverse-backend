package com.runiverse.running_service.application.running.command.combo;

import com.runiverse.running_service.application.running.port.out.RunningComboPeer;
import com.runiverse.running_service.application.running.port.out.RunningComboRelation;
import com.runiverse.running_service.domain.common.vo.UserId;

import java.util.List;

// 방 단위로 센 관계를 받는 사람 한 명의 화면 기준으로 돌려놓는다.
// 갱신 통지와 진입 스냅샷이 같은 모양을 보내야 클라가 한 벌의 코드로 그린다
public final class RunningComboPeers {

    private RunningComboPeers() {

    }

    // 방 전체의 관계에서 받는 사람이 낀 것만 추려 그 사람 기준으로 돌려놓는다.
    // 겹치는 상대가 없으면 빈 목록이고, 그것이 곧 "화면을 비우라"는 뜻이다
    public static List<RunningComboPeer> of(
            UserId recipient, List<RunningComboRelation> relations) {
        return relations.stream()
                .filter(relation -> relation.first().equals(recipient.value())
                        || relation.second().equals(recipient.value()))
                .map(relation -> toPeer(recipient, relation))
                .toList();
    }

    // gapMeters는 second가 앞설 때 양수로 실려 온다 —
    // 받는 쪽이 second면 뒤집어야 "양수면 상대가 앞"이 된다
    private static RunningComboPeer toPeer(UserId recipient, RunningComboRelation relation) {
        boolean receivedByFirst = relation.first().equals(recipient.value());
        return new RunningComboPeer(
                receivedByFirst ? relation.second() : relation.first(),
                receivedByFirst ? relation.gapMeters() : -relation.gapMeters(),
                relation.comboCount(),
                relation.maxComboCount());
    }
}
