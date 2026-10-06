package com.runiverse.running_service.application.running.port.out;

import java.time.LocalDateTime;

public interface LoadWeatherPort {

    Weather load(double latitude, double longitude, LocalDateTime at);
}
