package com.runiverse.running_service.presentation.app.response;

public record AppVersionResponse(
        String minimumVersion,
        String latestVersion
) {

}
