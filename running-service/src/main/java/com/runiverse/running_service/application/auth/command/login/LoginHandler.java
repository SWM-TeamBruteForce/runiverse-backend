package com.runiverse.running_service.application.auth.command.login;

import com.runiverse.running_service.application.auth.exception.InvalidCredentialsException;
import com.runiverse.running_service.application.auth.port.in.LoginUsecase;
import com.runiverse.running_service.application.auth.port.out.GenerateTokenPort;
import com.runiverse.running_service.application.auth.port.out.LoadUserByEmailPort;
import com.runiverse.running_service.application.auth.port.out.RefreshTokenHashPort;
import com.runiverse.running_service.application.auth.port.out.SaveRefreshTokenHashPort;
import com.runiverse.running_service.application.common.port.out.PasswordHashPort;
import com.runiverse.running_service.application.device.port.out.DeactivateUserDevicesPort;
import com.runiverse.running_service.domain.user.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class LoginHandler implements LoginUsecase {

    private final LoadUserByEmailPort loadUserByEmailPort;
    private final PasswordHashPort passwordHashPort;
    private final GenerateTokenPort generateTokenPort;
    private final RefreshTokenHashPort refreshTokenHashPort;
    private final SaveRefreshTokenHashPort saveRefreshTokenPort;
    private final DeactivateUserDevicesPort deactivateUserDevicesPort;

    @Override
    public LoginResult handle(LoginCommand command) {
        // 1. 이메일 확인 — 대소문자를 구분한다. 가입 때 소문자로 정규화해 저장하므로 클라이언트가 소문자로 보낸다
        Optional<User> foundUser = loadUserByEmailPort.loadByEmail(command.email());
        if (foundUser.isEmpty()) {
            log.info("[인증] 로그인 실패: 가입되지 않은 이메일");
            throw new InvalidCredentialsException();
        }
        User user = foundUser.get();

        // 2. 비밀번호 확인
        boolean passwordChecked = passwordHashPort.matches(
                command.password(),
                user.getPasswordHash().value());
        if (!passwordChecked) {
            log.info("[인증] 로그인 실패: 비밀번호 불일치 - userId={}", user.getUserId().value());
            throw new InvalidCredentialsException();
        }

        // 3. 다른 기기에서 쓰던 세션이 이 로그인으로 끝난다 — 그 기기들로 푸시가 가지 않게 끈다.
        //    토큰 저장보다 먼저 해야 저장이 실패했을 때 함께 되돌아간다
        deactivateUserDevicesPort.deactivateAll(user.getUserId());

        // 4. jwt 토큰 생성
        String accessToken = generateTokenPort.generateAccessToken(user.getUserId());
        String refreshToken = generateTokenPort.generateRefreshToken(user.getUserId());

        // 5. refresh token 해시화
        String hashedRefreshToken = refreshTokenHashPort.hash(refreshToken);

        // 6. refresh token redis 저장
        saveRefreshTokenPort.save(user.getUserId(), hashedRefreshToken);
        log.info("[인증] 로그인 성공 - userId={}", user.getUserId().value());

        // 7. 반환
        return new LoginResult(user.getUserId().value(), accessToken, refreshToken);
    }
}
