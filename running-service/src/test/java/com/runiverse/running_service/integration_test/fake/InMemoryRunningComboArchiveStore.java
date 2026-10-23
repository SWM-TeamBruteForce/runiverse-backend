package com.runiverse.running_service.integration_test.fake;

import com.runiverse.running_service.application.running.port.out.CreateRunningCombosPort;
import com.runiverse.running_service.domain.running.combo.RunningCombo;

import java.util.ArrayList;
import java.util.List;

// RunningComboPersistenceAdapter를 대신한다 — 넣은 콤보 구간을 그대로 쌓는다
public class InMemoryRunningComboArchiveStore implements CreateRunningCombosPort {

    private final List<RunningCombo> combos = new ArrayList<>();

    @Override
    public void createAll(List<RunningCombo> saved) {
        combos.addAll(saved);
    }

    // 검증 전용
    public List<RunningCombo> findByRoom(Long runningRoomId) {
        return combos.stream()
                .filter(combo -> combo.getRunningRoomId().value().equals(runningRoomId))
                .toList();
    }
}
