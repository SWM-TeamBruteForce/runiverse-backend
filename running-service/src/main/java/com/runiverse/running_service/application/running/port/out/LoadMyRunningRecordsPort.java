package com.runiverse.running_service.application.running.port.out;

import com.runiverse.running_service.domain.common.vo.UserId;

import java.time.LocalDateTime;
import java.util.List;

public interface LoadMyRunningRecordsPort {

    // 시작 시각 오름차순. 기간에 기록이 없으면 빈 목록
    List<RunningRecordRow> loadByPeriod(
            UserId userId, LocalDateTime startInclusive, LocalDateTime endExclusive);
}
