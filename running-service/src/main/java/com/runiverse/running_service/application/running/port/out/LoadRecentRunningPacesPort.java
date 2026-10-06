package com.runiverse.running_service.application.running.port.out;

import com.runiverse.running_service.domain.common.vo.UserId;

import java.util.List;

public interface LoadRecentRunningPacesPort {

    // 최신순 최대 limit건. 기록이 하나도 없으면 빈 목록
    List<RecentRunningPace> loadRecent(UserId userId, int limit);
}
