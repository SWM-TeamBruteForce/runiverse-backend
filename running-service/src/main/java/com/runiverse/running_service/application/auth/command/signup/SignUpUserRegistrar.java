package com.runiverse.running_service.application.auth.command.signup;

import com.runiverse.running_service.application.auth.exception.EmailAlreadyExistsException;
import com.runiverse.running_service.application.auth.port.out.CheckEmailDuplicatePort;
import com.runiverse.running_service.application.auth.port.out.GenerateUserIdPort;
import com.runiverse.running_service.application.auth.port.out.SaveUserPort;
import com.runiverse.running_service.application.common.port.out.PasswordHashPort;
import com.runiverse.running_service.domain.user.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class SignUpUserRegistrar {

    private final CheckEmailDuplicatePort checkEmailDuplicatePort;
    private final PasswordHashPort passwordHashPort;
    private final GenerateUserIdPort generateUserIdPort;
    private final SaveUserPort saveUserPort;

    @Transactional
    public User register(String email, String rawPassword) {
        // 1. 이메일 중복 확인
        boolean emailExists = checkEmailDuplicatePort.existsByEmail(email);
        if (emailExists) {
            log.info("[인증] 회원가입 실패: 이미 가입된 이메일");
            throw new EmailAlreadyExistsException();
        }

        // 2. 비밀번호 해시화
        String hashedPassword = passwordHashPort.hash(rawPassword);

        // 3. UUIDv7 생성
        UUID userId = generateUserIdPort.generate();

        // 4. 도메인 User 생성
        User user = new User(userId, email, hashedPassword);

        // 5. DB 저장
        return saveUserPort.save(user);
    }
}
