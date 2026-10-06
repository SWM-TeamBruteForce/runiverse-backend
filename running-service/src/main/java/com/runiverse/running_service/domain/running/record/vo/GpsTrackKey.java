package com.runiverse.running_service.domain.running.record.vo;

import com.runiverse.running_service.domain.running.record.exception.GpsTrackKeyRequiredException;
import com.runiverse.running_service.domain.running.record.exception.GpsTrackKeyTooLongException;

public record GpsTrackKey(String value) {

    private static final int MAX_LENGTH = 255;

    public GpsTrackKey {
        if (value == null) {
            throw new GpsTrackKeyRequiredException();
        }
        value = value.trim();
        if (value.isEmpty()) {
            throw new GpsTrackKeyRequiredException();
        }
        if (value.length() > MAX_LENGTH) {
            throw new GpsTrackKeyTooLongException();
        }
    }
}
