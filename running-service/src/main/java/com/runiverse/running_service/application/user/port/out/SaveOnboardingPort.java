package com.runiverse.running_service.application.user.port.out;

import com.runiverse.running_service.domain.user.UserOnboarding;

public interface SaveOnboardingPort {

    void saveOnboarding(UserOnboarding onboarding);
}
