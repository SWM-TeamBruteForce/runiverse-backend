package com.runiverse.running_service.infrastructure.weather.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.List;

public record OpenMeteoResponse(Hourly hourly) {

    public record Hourly(
            @JsonProperty("weather_code") List<Integer> weatherCode,
            @JsonProperty("temperature_2m") List<BigDecimal> temperature
    ) {

    }
}
