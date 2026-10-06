package com.runiverse.running_service.application.user.port.out;

import com.runiverse.running_service.domain.common.vo.UserId;

import java.util.Optional;

public interface LoadOnboardingProfilePort {

    // 온보딩 전이면 빈 Optional — 행이 없는 것이지 오류가 아니다
    Optional<OnboardingProfile> loadOnboardingProfile(UserId userId);
}
