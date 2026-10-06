package com.runiverse.running_service.application.user.port.out;

import com.runiverse.running_service.domain.user.vo.ProfileImageKey;
import com.runiverse.running_service.domain.common.vo.UserId;

public interface UpdateProfileImagePort {

    void updateProfileImage(UserId userId, ProfileImageKey profileImageKey);
}
