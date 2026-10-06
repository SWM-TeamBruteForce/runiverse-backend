package com.runiverse.running_service.integration_test.fake;

import com.runiverse.running_service.application.running.port.out.LoadWeatherPort;
import com.runiverse.running_service.application.running.port.out.Weather;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// DefaultWeatherAdapter를 대신한다 — 외부 API가 붙기 전이라 실제 어댑터도 기본값만 준다
public class FakeWeatherProvider implements LoadWeatherPort {

    public static final Weather DEFAULT = new Weather(0, new BigDecimal("15.0"));

    private Weather weather = DEFAULT;

    @Override
    public Weather load(double latitude, double longitude, LocalDateTime at) {
        return weather;
    }

    // 테스트 준비 — 날씨가 기록에 그대로 실리는지 볼 때 바꾼다
    public void set(Weather weather) {
        this.weather = weather;
    }
}
