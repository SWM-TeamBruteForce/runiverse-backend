package com.runiverse.running_service.application.auth.command.oauthlogin;

import com.runiverse.running_service.application.auth.exception.EmailAlreadyExistsException;
import com.runiverse.running_service.application.auth.port.out.CheckEmailDuplicatePort;
import com.runiverse.running_service.application.auth.port.out.GenerateUserIdPort;
import com.runiverse.running_service.application.auth.port.out.LoadUserByProviderPort;
import com.runiverse.running_service.application.auth.port.out.OauthProfile;
import com.runiverse.running_service.application.auth.port.out.SaveUserPort;
import com.runiverse.running_service.domain.user.User;
import com.runiverse.running_service.domain.user.vo.Email;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class OauthUserResolver {

    private final LoadUserByProviderPort loadUserByProviderPort;
    private final CheckEmailDuplicatePort checkEmailDuplicatePort;
    private final GenerateUserIdPort generateUserIdPort;
    private final SaveUserPort saveUserPort;

    @Transactional
    public User findOrRegister(OauthProfile oauthProfile) {
        // 1. (provider, providerId)로 기존 유저 조회
        Optional<User> foundUser = loadUserByProviderPort.loadByProvider(
                oauthProfile.provider(),
                oauthProfile.providerId()
        );

        // 2. 있으면 그대로 반환
        if (foundUser.isPresent()) {
            return foundUser.get();
        }

        // 3. 없으면 가입
        return register(oauthProfile);
    }

    private User register(OauthProfile oauthProfile) {
        // 1. 저장할 형태로 정규화한다 — 검사와 생성이 다른 값을 쓰면 UNIQUE 위반으로 500이 된다
        String email = new Email(oauthProfile.email()).value();

        // 2. 기존 로컬 계정과 이메일이 겹치면 자동 연동하지 않는다
        if (checkEmailDuplicatePort.existsByEmail(email)) {
            log.info("[인증] 소셜 로그인 실패: 이미 가입된 이메일 - provider={}", oauthProfile.provider());
            throw new EmailAlreadyExistsException();
        }

        // 3. UUIDv7 발급
        UUID userId = generateUserIdPort.generate();

        // 4. 유저 생성 + 소셜 연결
        User user = User.registerWithOauth(
                userId,
                email,
                oauthProfile.provider(),
                oauthProfile.providerId()
        );

        // 5. 저장
        return saveUserPort.save(user);
    }

}
