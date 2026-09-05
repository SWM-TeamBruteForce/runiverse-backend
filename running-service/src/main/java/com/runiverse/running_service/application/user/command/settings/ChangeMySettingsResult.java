package com.runiverse.running_service.application.user.command.settings;

// 보낸 필드만이 아니라 저장 후 설정 전체다 — 클라이언트가 이 값으로 설정 화면을 갱신한다
public record ChangeMySettingsResult(
        boolean alertConsent,
        String profileVisibility
) {

}
