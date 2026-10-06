package com.runiverse.running_service.application.running.port.in;

import com.runiverse.running_service.application.running.command.finish.FinishRunningCommand;

public interface FinishRunningUsecase {

    // 반환값이 없다 — RUNNING_FINISHED ack에 실을 데이터가 없고,
    // 클라는 ack를 받은 뒤 REST로 결과를 조회한다
    void handle(FinishRunningCommand command);
}
