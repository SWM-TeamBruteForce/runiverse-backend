package com.runiverse.running_service.application.user.command.accountdeletion;

import com.runiverse.running_service.domain.user.vo.ProviderId;

// 탈퇴가 커밋된 뒤에 카카오 연동을 끊으라는 요청 — 발행 시점에는 아직 연결이 살아 있다.
// 커밋 전에 끊으면 탈퇴가 롤백돼도 연동만 사라진다
public record KakaoUnlinkRequestedEvent(ProviderId providerId) {

}
