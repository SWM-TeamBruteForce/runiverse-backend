package com.runiverse.running_service.application.user.query.account;

import com.runiverse.running_service.application.user.exception.UserNotFoundException;
import com.runiverse.running_service.application.user.port.in.GetMyAccountUsecase;
import com.runiverse.running_service.application.user.port.out.LoadOauthProviderPort;
import com.runiverse.running_service.application.user.port.out.LoadUserByIdPort;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.user.aggregate.User;
import com.runiverse.running_service.domain.user.vo.LoginType;
import com.runiverse.running_service.domain.user.vo.Provider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GetMyAccountHandler implements GetMyAccountUsecase {

    private final LoadUserByIdPort loadUserByIdPort;
    private final LoadOauthProviderPort loadOauthProviderPort;

    @Override
    public GetMyAccountResult handle(GetMyAccountQuery query) {
        UserId userId = new UserId(query.userId());

        User user = loadUserByIdPort.loadById(userId)
                .orElseThrow(UserNotFoundException::new);

        // 애그리거트로는 판정할 수 없다 — loadById가 oauthUser를 복원하지 않는다
        Optional<Provider> provider = loadOauthProviderPort.loadProvider(userId);

        return new GetMyAccountResult(
                user.getEmail().value(),
                LoginType.from(provider.orElse(null)).name()
        );
    }
}
