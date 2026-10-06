package com.runiverse.running_service.application.user.port.in;

import com.runiverse.running_service.application.user.command.onboarding.CompleteOnboardingCommand;
import com.runiverse.running_service.application.user.command.onboarding.CompleteOnboardingResult;

public interface CompleteOnboardingUsecase {

    CompleteOnboardingResult handle(CompleteOnboardingCommand command);
}
