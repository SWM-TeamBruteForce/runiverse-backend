package com.runiverse.running_service.application.running.query.status;

import java.util.UUID;

public record GetUserStatusQuery(
        UUID userId
) {

}
