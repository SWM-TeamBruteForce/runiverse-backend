package com.runiverse.running_service.application.user.command.onboarding;

import java.util.UUID;

public record CompleteOnboardingResult(UUID userId, String nickname) {

}
