package com.runiverse.running_service.application.user.query.settings;

public record GetMySettingsResult(
        boolean alertConsent,
        String profileVisibility
) {

}
