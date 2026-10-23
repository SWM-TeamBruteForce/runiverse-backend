package com.runiverse.running_service.application.user.common;

import com.runiverse.running_service.application.user.port.out.GenerateViewUrlPort;
import com.runiverse.running_service.domain.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

// 사진 key를 조회용 URL로 바꾼다 — 사진이 없으면 URL도 없다
@Component
@RequiredArgsConstructor
public class UserImageUrlResolver {

    private final GenerateViewUrlPort generateViewUrlPort;

    public String resolve(UserImageType type, User user) {
        return type.keyOf(user)
                .map(generateViewUrlPort::generate)
                .orElse(null);
    }
}
