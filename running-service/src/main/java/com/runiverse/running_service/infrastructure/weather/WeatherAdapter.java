package com.runiverse.running_service.infrastructure.weather;

import com.runiverse.running_service.application.running.port.out.LoadWeatherPort;
import com.runiverse.running_service.application.running.port.out.Weather;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;

import java.time.LocalDateTime;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class WeatherAdapter implements LoadWeatherPort {

    private final OpenMeteoWeatherClient client;
    private final WeatherProperties properties;

    @Override
    public Weather load(double latitude, double longitude, LocalDateTime at) {
        for (int attempt = 1; attempt <= properties.maxAttempts(); attempt++) {
            try {
                Optional<Weather> weather = client.fetch(latitude, longitude, at);
                if (weather.isPresent()) {
                    return weather.get();
                }
                break; // 값이 없는 응답은 재시도해도 같다
            } catch (HttpClientErrorException e) {
                // 잘못된 좌표·파라미터는 400으로 온다 — 같은 요청을 되풀이할 이유가 없다
                log.warn("날씨 요청이 거부됐다: lat={}, lon={}", latitude, longitude, e);
                break;
            } catch (RestClientException e) {
                log.warn("날씨 조회 실패 {}/{}", attempt, properties.maxAttempts(), e);
            }
        }
        // 한 시간짜리 러닝 기록이 외부 API 하나 때문에 날아가면 안 된다(feature-spec §2)
        return new Weather(properties.defaultCode(), properties.defaultTemperature());
    }
}
