package com.runiverse.running_service.infrastructure.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
public class SchedulingConfig {
    // 매칭 스트림 keep-alive가 첫 사용자다. 모집 마감 스케줄러도 이 설정을 탄다
}
