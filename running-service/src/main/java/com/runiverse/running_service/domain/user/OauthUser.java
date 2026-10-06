package com.runiverse.running_service.domain.user;

import com.runiverse.running_service.domain.user.exception.ProviderRequiredException;
import com.runiverse.running_service.domain.common.exception.UserIdRequiredException;
import com.runiverse.running_service.domain.user.vo.Provider;
import com.runiverse.running_service.domain.user.vo.ProviderId;
import com.runiverse.running_service.domain.common.vo.UserId;
import lombok.Getter;

import java.util.Objects;

@Getter
public class OauthUser {

    private final UserId userId;
    private final Provider provider;
    private final ProviderId providerId;

    OauthUser(UserId userId, Provider provider, String providerId) {
        if (userId == null) {
            throw new UserIdRequiredException();// provider는 application에서 검증
        }
        if (provider == null) {
            throw new ProviderRequiredException();
        }
        this.userId = userId;
        this.provider = provider;
        this.providerId = new ProviderId(providerId);
    }

    public boolean isSameProvider(Provider other) {
        return provider == other;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof OauthUser other)) {
            return false;
        }
        return userId.equals(other.userId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId);
    }
}
