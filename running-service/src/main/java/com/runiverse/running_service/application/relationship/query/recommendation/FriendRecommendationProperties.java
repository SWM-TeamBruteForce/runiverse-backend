package com.runiverse.running_service.application.relationship.query.recommendation;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "friend-recommendation")
@Validated
public record FriendRecommendationProperties(
        // 함께 뛴 사람을 먼저 채우고 남은 자리를 페이스가 비슷한 사람으로 채운다 — 페이지를 나누지 않는 상한이다
        @NotNull @Positive Integer maxCount,
        // 평균 페이스 차이가 이 값 이내면 비슷하다고 본다. 그룹런에서 한 페이스 조로 묶이는 정도다
        @NotNull @Positive Integer paceToleranceSecondsPerKm
) {

}
