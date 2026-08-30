package com.runiverse.running_service.infrastructure.weather;

import com.runiverse.running_service.application.running.port.out.Weather;
import com.runiverse.running_service.infrastructure.weather.dto.OpenMeteoResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

@Slf4j
@Component
public class OpenMeteoWeatherClient {

    private static final DateTimeFormatter HOUR = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:00");
    private final RestClient restClient;

    OpenMeteoWeatherClient(RestClient.Builder builder, WeatherProperties properties) {
        // oauthRestClient 빈과 타입이 겹치면 주입이 모호해진다 — Builder로 따로 만든다
        this.restClient = builder
                .baseUrl(properties.baseUri())
                .requestFactory(ClientHttpRequestFactoryBuilder.detect()
                        .build(HttpClientSettings.defaults()
                                .withTimeouts(properties.timeout(), properties.timeout())))
                .build();
    }

    // 통신 실패는 예외로 던지고 값 없음은 Optional.empty로 구분한다 —
    // 앞은 재시도할 값어치가 있고 뒤는 다시 물어도 같은 답이다
    Optional<Weather> fetch(double latitude, double longitude, LocalDateTime at) {
        // 저장 시각과 조회 시각의 기준을 하나로 묶는다 —
        // TimeZoneConfig가 JVM 기본 존을 APP_TIME_ZONE으로 고정해둔다
        ZoneId zone = ZoneId.systemDefault();
        OpenMeteoResponse response = restClient.get()
                .uri(uri -> uri.path("/v1/forecast")
                        .queryParam("latitude", latitude)
                        .queryParam("longitude", longitude)
                        .queryParam("hourly", "weather_code,temperature_2m")
                        .queryParam("timezone", zone.getId())
                        // 한 시각만 집어 온다. past_days는 이 둘과 함께 쓸 수 없고,
                        // 지난 시각도 이 파라미터만으로 조회된다
                        .queryParam("start_hour", at.format(HOUR))
                        .queryParam("end_hour", at.format(HOUR))
                        .build())
                .retrieve()
                .body(OpenMeteoResponse.class);
        return toWeather(response);
    }

    private Optional<Weather> toWeather(OpenMeteoResponse response) {
        if (response == null || response.hourly() == null) {
            log.warn("날씨 응답이 비어 있다");
            return Optional.empty();
        }
        Integer code = first(response.hourly().weatherCode());
        BigDecimal temperature = first(response.hourly().temperature());
        // 반쪽 값을 기록에 넣느니 폴백 기본값이 낫다 — 두 값은 함께 판정에 쓰인다
        if (code == null || temperature == null) {
            log.warn("날씨 응답에 값이 없다: {}", response);
            return Optional.empty();
        }
        return Optional.of(new Weather(code, temperature));
    }

    private <T> T first(List<T> values) {
        return values == null || values.isEmpty() ? null : values.get(0);
    }
}
