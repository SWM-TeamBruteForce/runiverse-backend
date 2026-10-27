package com.runiverse.running_service.application.device.command.push;

// 커밋된 뒤에 푸시를 보내라는 요청 — 커밋 전에 보내면 롤백돼도 알림만 나간다
public record PushRequestedEvent(SendPushCommand command) {

}
