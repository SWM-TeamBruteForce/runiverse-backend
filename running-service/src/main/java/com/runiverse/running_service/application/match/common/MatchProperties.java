package com.runiverse.running_service.application.match.common;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.time.LocalTime;
import java.util.List;

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
        @NotNull Duration cooldown,
        // 신청 가능 시각·거리 — 신청 검증과 슬롯 조회가 MatchSlotPolicy로 함께 읽는다.
        // 기본 LocalTime 변환은 로케일 짧은 형식(오후 6:00)을 기대해 18:00을 못 읽는다
        @NotNull @DateTimeFormat(pattern = "HH:mm") LocalTime allowedFirstStartTime,
        @NotNull @DateTimeFormat(pattern = "HH:mm") LocalTime allowedLastStartTime,
        @NotNull Duration allowedStartTimeInterval,
        // 순서대로 검증 문구에 나간다
        @NotEmpty List<@NotNull @Positive Integer> allowedDistancesMeters
) {

    // null 검사는 @NotNull이 따로 보고한다 — 여기서 터지면 원인이 가려진다
    @AssertTrue(message = "첫 시작 시각은 마지막 시작 시각보다 늦을 수 없다")
    public boolean isStartTimeOrdered() {
        return allowedFirstStartTime == null || allowedLastStartTime == null
                || !allowedFirstStartTime.isAfter(allowedLastStartTime);
    }

    // 분 단위가 아니면 "N분 간격" 문구를 만들 수 없다
    @AssertTrue(message = "시작 시각 간격은 1분 이상, 분 단위여야 한다")
    public boolean isIntervalWholeMinutes() {
        return allowedStartTimeInterval == null
                || (allowedStartTimeInterval.toMinutes() > 0
                && allowedStartTimeInterval.toSeconds() % 60 == 0
                && allowedStartTimeInterval.toNanosPart() == 0);
    }

    // 나누어떨어지지 않으면 마지막 시작 시각이 슬롯이 아니게 된다 — "~까지" 문구가 거짓말이 된다
    @AssertTrue(message = "첫·마지막 시작 시각의 차이는 간격으로 나누어떨어져야 한다")
    public boolean isLastStartTimeOnInterval() {
        if (allowedFirstStartTime == null || allowedLastStartTime == null
                || allowedStartTimeInterval == null || allowedStartTimeInterval.toNanos() <= 0) {
            return true;
        }
        return Duration.between(allowedFirstStartTime, allowedLastStartTime).toNanos()
                % allowedStartTimeInterval.toNanos() == 0;
    }
}
