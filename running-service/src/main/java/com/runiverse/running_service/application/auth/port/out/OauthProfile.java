package com.runiverse.running_service.application.auth.port.out;

import com.runiverse.running_service.domain.user.vo.Provider;

public record OauthProfile(
        Provider provider,
        String providerId,
        String email
) {

}
