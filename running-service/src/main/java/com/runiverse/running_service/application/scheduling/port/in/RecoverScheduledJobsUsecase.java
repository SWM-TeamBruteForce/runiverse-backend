package com.runiverse.running_service.application.scheduling.port.in;

public interface RecoverScheduledJobsUsecase {

    // 타이머는 메모리에만 있어 재시작하면 사라진다 — 부팅 때 DB에서 되살린다
    void recover();
}
