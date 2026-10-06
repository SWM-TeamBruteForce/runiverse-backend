package com.runiverse.running_service.application.user.query.basicinfo;

import java.util.UUID;

public record GetMyBasicInfoResult(
        UUID userId,
        String email,
        String loginType,
        String nickname,   // 온보딩 전이면 null — 닉네임은 온보딩에서 처음 생긴다
        boolean isOnboarded
) {

}
