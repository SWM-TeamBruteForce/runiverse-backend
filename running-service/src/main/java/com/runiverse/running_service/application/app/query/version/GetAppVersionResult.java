package com.runiverse.running_service.application.app.query.version;

public record GetAppVersionResult(
        String minimumVersion,
        String latestVersion
) {

}
