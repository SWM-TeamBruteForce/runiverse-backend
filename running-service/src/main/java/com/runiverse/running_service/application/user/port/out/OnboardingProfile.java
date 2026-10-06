package com.runiverse.running_service.application.user.port.out;

import com.runiverse.running_service.domain.user.vo.Birthday;
import com.runiverse.running_service.domain.user.vo.Gender;
import com.runiverse.running_service.domain.user.vo.Height;
import com.runiverse.running_service.domain.user.vo.Weight;

// user_onboardings에서 프로필 편집이 읽는 값만 담는다.
// UserOnboarding 애그리거트를 그대로 돌려주지 않는 건 닉네임·평균 페이스가 이 화면의 값이 아니어서다
public record OnboardingProfile(Gender gender, Birthday birthday, Weight weight, Height height) {

}
