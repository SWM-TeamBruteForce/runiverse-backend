package com.runiverse.running_service.application.running.port.out;

import com.runiverse.running_service.domain.running.combo.RunningCombo;

import java.util.List;

public interface CreateRunningCombosPort {

    // 방 하나치를 한 번에 넣는다 — write-once라 고치는 경로가 없다
    void createAll(List<RunningCombo> combos);
}
