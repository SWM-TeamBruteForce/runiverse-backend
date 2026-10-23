package com.runiverse.running_service.application.running.port.in;

import com.runiverse.running_service.application.running.command.finish.FinishRunningCommand;
import com.runiverse.running_service.application.running.common.GoalCheck;

public interface FinishRunningUsecase {

    // 끝났으면 RUNNING_FINISHED ack를 보낸다 — ack에 실을 데이터는 없고 클라는 REST로 결과를 조회한다.
    // 다 뛰었다고 보고 누른 종료(forced=false)가 확정되지 않았으면 남은 거리를 돌려준다
    GoalCheck handle(FinishRunningCommand command);
}
