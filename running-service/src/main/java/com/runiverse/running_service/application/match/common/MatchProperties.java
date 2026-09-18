package com.runiverse.running_service.application.match.common;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@ConfigurationProperties(prefix = "match")
@Validated
public record MatchProperties(
        // 모집 마감 = start_at - 이 값. 컬럼에 저장하지 않는다 —
        // 오프셋을 바꾸는 건 곧 정책을 바꾸는 것이라 진행 중인 방만 옛 마감을 유지할 이유가 없다
        @NotNull Duration closeOffset,
        // 시작 통지 = start_at - 이 값. 정각에 보내면 클라의 발사 시점과 겹쳐 늘 늦게 도착한다 —
        // 클라가 타이머를 걸 여유만 주면 되므로 WS 연결 시간까지 덮을 필요는 없다
        @NotNull Duration readyOffset,
        // 강제 종료 = start_at + 이 값. 앱이 죽거나 네트워크가 끊겨 종료 메시지가 영영 오지 않는 방을
        // 서버가 대신 닫는다. 방이 열려 있는 동안 참가자의 신청도 활성이라 다음 러닝이 막힌다
        @NotNull Duration forceFinishOffset,
        // 페이스 차이가 이 값 이내면 동급으로 보고 내 이탈 이력으로 순위를 가른다.
        // 후보 자격 판정은 없다 — 페이스는 순서만 정한다.
        // 이 값이 곧 "페이스 위주 ↔ 이력 위주" 손잡이다: 키우면 동급이 늘어 이력이 자주 개입한다
        @NotNull @Positive Integer paceTieToleranceSecondsPerKm,
        // 제재 대상 이탈 후 재신청이 막히는 기간. Redis 키의 TTL로 쓴다
        @NotNull Duration cooldown
) {

}
