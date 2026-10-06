package com.runiverse.running_service.application.user.port.out;

import com.runiverse.running_service.domain.user.vo.Nickname;

public interface CheckNicknameDuplicatePort {

    boolean existsByNickname(Nickname nickname);
}
