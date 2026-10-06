package com.runiverse.running_service.application.match.common;

import com.runiverse.running_service.application.match.port.out.MatchStreamEvent;

// 스프링 애플리케이션 이벤트 — 유스케이스와 발신 시점을 갈라놓는 얇은 껍데기다.
// 대상 방은 event.room().runningRoomId()가 갖는다
public record MatchRoomChangedEvent(MatchStreamEvent event) {

}
