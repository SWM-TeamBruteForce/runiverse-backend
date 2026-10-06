package com.runiverse.running_service.application.match.command.stream;

import com.runiverse.running_service.domain.common.vo.UserId;

// 회원탈퇴가 커밋된 뒤에 스트림을 닫으라는 요청 — 발행 시점에는 아직 열려 있다.
// 커밋 전에 닫으면 탈퇴가 롤백돼도 연결만 끊긴 상태가 남는다
public record MatchStreamCloseRequestedEvent(UserId userId) {

}
