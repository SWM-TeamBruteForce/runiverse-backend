package com.runiverse.running_service.application.user.port.out;

import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.user.vo.ProfileVisibility;

public interface UpdateSettingsPort {

    // null인 인자는 갱신하지 않는다 — 부분 수정이라 담겨 온 값만 바꾼다
    void updateSettings(UserId userId, Boolean alertConsent, ProfileVisibility profileVisibility);
}
