package com.runiverse.running_service.application.user.port.out;

import com.runiverse.running_service.domain.user.vo.Nickname;
import com.runiverse.running_service.domain.common.vo.UserId;

import java.util.Optional;

public interface LoadNicknamePort {

    Optional<Nickname> loadNickname(UserId userId);
}
