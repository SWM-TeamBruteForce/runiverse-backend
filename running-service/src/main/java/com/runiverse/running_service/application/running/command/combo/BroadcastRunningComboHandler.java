package com.runiverse.running_service.application.running.command.combo;

import com.runiverse.running_service.application.running.port.in.BroadcastRunningComboUsecase;
import com.runiverse.running_service.application.running.port.out.LoadRunningRoomMembersPort;
import com.runiverse.running_service.application.running.port.out.RunningComboPeer;
import com.runiverse.running_service.application.running.port.out.RunningComboRelation;
import com.runiverse.running_service.application.running.port.out.RunningSessionPort;
import com.runiverse.running_service.domain.common.vo.UserId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class BroadcastRunningComboHandler implements BroadcastRunningComboUsecase {

    private final LoadRunningRoomMembersPort loadRunningRoomMembersPort;
    private final RunningSessionPort runningSessionPort;

    // 이 인스턴스에 붙어 있는 수신자에게만 보낸다 — 다른 서버의 참가자는 그쪽이 같은 메시지를 받아 처리한다.
    // 진행 통지와 달리 보낸 사람도 받는다: 본인 콤보는 서버가 시각에서 계산한 값이라 클라가 스스로 못 만든다
    // 이 인스턴스에 붙어 있는 수신자에게만 보낸다 — 다른 서버의 참가자는 그쪽이 같은 메시지를 받아 처리한다.
    @Override
    public void handle(BroadcastRunningComboCommand command) {
        Set<UserId> members = loadRunningRoomMembersPort.usersIn(command.runningRoomId());
        command.update().recipients().stream()
                .map(UserId::new)
                // 방을 옮긴 참가자에게 옛 방의 콤보를 밀어 넣지 않는다
                .filter(members::contains)
                .forEach(recipient -> runningSessionPort.find(recipient)
                        .ifPresent(connection -> connection.sendCombo(
                                peersOf(recipient, command.update().relations()))));
    }

    // 방 전체의 관계에서 받는 사람이 낀 것만 추려 그 사람 기준으로 돌려놓는다.
    // 겹치는 상대가 없으면 빈 목록이고, 그것이 곧 "화면을 비우라"는 뜻이다
    private static List<RunningComboPeer> peersOf(
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
