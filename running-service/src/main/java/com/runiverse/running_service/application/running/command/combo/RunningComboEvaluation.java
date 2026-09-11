package com.runiverse.running_service.application.running.command.combo;

import com.runiverse.running_service.application.running.port.out.RunningComboPair;
import com.runiverse.running_service.application.running.port.out.RunningComboUpdate;

import java.util.List;

// 판정 한 번의 산물 — 저장할 관계 상태와 내보낼 통이다
public record RunningComboEvaluation(
        // 배치를 보낸 참가자가 낀 관계만 담긴다. 나머지 관계는 값이 바뀌지 않아 저장할 것이 없다
        List<RunningComboPair> pairs,
        RunningComboUpdate update
) {

}
