package com.runiverse.running_service.application.scheduling.command.schedule;

import com.runiverse.running_service.domain.scheduling.ScheduledJob;

// 스프링 애플리케이션 이벤트 — 저장과 "알리기"의 시점을 갈라놓는 얇은 껍데기다
public record ScheduledJobCreatedEvent(ScheduledJob job) {

}
