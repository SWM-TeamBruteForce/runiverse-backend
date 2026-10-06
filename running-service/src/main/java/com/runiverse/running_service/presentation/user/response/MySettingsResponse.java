package com.runiverse.running_service.presentation.user.response;

// 조회와 수정이 같은 형식을 쓴다 — 수정도 갱신한 필드가 아니라 설정 전체를 돌려준다
public record MySettingsResponse(
        boolean alertConsent,
        String profileVisibility
) {

}
