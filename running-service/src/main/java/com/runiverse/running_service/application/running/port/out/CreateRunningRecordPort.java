package com.runiverse.running_service.application.running.port.out;

import com.runiverse.running_service.domain.running.record.RunningRecord;

public interface CreateRunningRecordPort {

    // 기록과 구간을 한 애그리거트로 저장한다 — 구간은 RunningRecord 안에 있다
    void create(RunningRecord record);
}
