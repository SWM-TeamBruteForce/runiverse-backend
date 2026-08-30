package com.runiverse.running_service.infrastructure.weather;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;
import java.time.Duration;

@ConfigurationProperties(prefix = "weather")
@Validated
public record WeatherProperties(
        // WMO 4677. 기본값은 악조건이 아닌 쪽으로 고른다 — 실측이 아닌 값이 섞이더라도
        // 향후 ADVERSITY 컬러 판정에서 없던 악조건을 만들어내지 않게 한다(feature-spec §2)
        @NotNull @Min(0) @Max(99) Integer defaultCode,
        @NotNull BigDecimal defaultTemperature,
        @NotBlank String baseUri,
        @NotNull @Min(1) Integer maxAttempts,
        // 이 호출이 러닝 종료 응답을 붙잡는다 — 전역 설정보다 짧게 둔다
        @NotNull Duration timeout
) {

}
