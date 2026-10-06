package com.runiverse.running_service.infrastructure.redis.scheduling;

import com.runiverse.running_service.domain.scheduling.vo.ScheduledJobType;

import java.time.LocalDateTime;

public record ScheduledJobMessage(Long scheduledJobId, ScheduledJobType type,
                                  String targetId, LocalDateTime executeAt) {

}
