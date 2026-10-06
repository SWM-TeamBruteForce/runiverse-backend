package com.runiverse.running_service.application.user.query.basicinfo;

import com.runiverse.running_service.application.user.exception.UserNotFoundException;
import com.runiverse.running_service.application.user.port.in.GetMyBasicInfoUsecase;
import com.runiverse.running_service.application.user.port.out.LoadNicknamePort;
import com.runiverse.running_service.application.user.port.out.LoadOauthProviderPort;
import com.runiverse.running_service.application.user.port.out.LoadUserByIdPort;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.user.User;
import com.runiverse.running_service.domain.user.vo.LoginType;
import com.runiverse.running_service.domain.user.vo.Nickname;
import com.runiverse.running_service.domain.user.vo.Provider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GetMyBasicInfoHandler implements GetMyBasicInfoUsecase {

    private final LoadUserByIdPort loadUserByIdPort;
    private final LoadNicknamePort loadNicknamePort;
    private final LoadOauthProviderPort loadOauthProviderPort;

    @Override
    public GetMyBasicInfoResult handle(GetMyBasicInfoQuery query) {
        UserId userId = new UserId(query.userId());

        // 1. 토큰이 가리키는 계정이 남아 있는지 확인
        User user = loadUserByIdPort.loadById(userId)
                .orElseThrow(UserNotFoundException::new);

        // 2. 닉네임은 온보딩에서 처음 생긴다 — 있으면 온보딩을 마친 것이다
        Optional<Nickname> nickname = loadNicknamePort.loadNickname(userId);

        // 3. 로그인 수단은 oauth_users 행 유무로 가른다.
        //    애그리거트로는 판정할 수 없다 — loadById가 소셜 연결을 복원하지 않는다
        Optional<Provider> provider = loadOauthProviderPort.loadProvider(userId);

        return new GetMyBasicInfoResult(
                user.getUserId().value(),
                user.getEmail().value(),
                LoginType.from(provider.orElse(null)).name(),
                nickname.map(Nickname::value).orElse(null),
                nickname.isPresent()
        );
    }
}
