package com.runiverse.running_service.application.user.port.out;

import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.user.vo.Birthday;
import com.runiverse.running_service.domain.user.vo.Gender;
import com.runiverse.running_service.domain.user.vo.Height;
import com.runiverse.running_service.domain.user.vo.Weight;

public interface UpdateOnboardingPort {

    // null인 값은 갱신하지 않는다 — 닉네임은 UpdateNicknamePort가, 평균 페이스는 러닝 기록이 맡는다
    void updateOnboarding(UserId userId, Gender gender, Birthday birthday,
                          Weight weight, Height height);
}
