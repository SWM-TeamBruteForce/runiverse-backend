package com.runiverse.running_service.application.auth.command.emailverification;

import com.runiverse.running_service.application.auth.exception.EmailAlreadyExistsException;
import com.runiverse.running_service.application.auth.exception.EmailVerificationCooldownException;
import com.runiverse.running_service.application.auth.exception.EmailVerificationDailyLimitExceededException;
import com.runiverse.running_service.application.auth.port.in.SendEmailVerificationUsecase;
import com.runiverse.running_service.application.auth.port.out.AcquireSendCooldownPort;
import com.runiverse.running_service.application.auth.port.out.CheckDailySendLimitPort;
import com.runiverse.running_service.application.auth.port.out.CheckEmailDuplicatePort;
import com.runiverse.running_service.application.auth.port.out.DeleteVerificationCodePort;
import com.runiverse.running_service.application.auth.port.out.GenerateVerificationCodePort;
import com.runiverse.running_service.application.auth.port.out.ReleaseSendCooldownPort;
import com.runiverse.running_service.application.auth.port.out.SaveVerificationCodePort;
import com.runiverse.running_service.application.auth.port.out.SendEmailPort;
import com.runiverse.running_service.application.auth.port.out.VerificationCodeHashPort;
import com.runiverse.running_service.domain.user.vo.Email;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SendEmailVerificationHandler implements SendEmailVerificationUsecase {

    private static final String SUBJECT = "[Runiverse] 이메일 인증 코드";
    private final AcquireSendCooldownPort acquireSendCooldownPort;
    private final ReleaseSendCooldownPort releaseSendCooldownPort;
    private final CheckDailySendLimitPort checkDailySendLimitPort;
    private final CheckEmailDuplicatePort checkEmailDuplicatePort;
    private final GenerateVerificationCodePort generateVerificationCodePort;
    private final VerificationCodeHashPort verificationCodeHashPort;
    private final SaveVerificationCodePort saveVerificationCodePort;
    private final DeleteVerificationCodePort deleteVerificationCodePort;
    private final SendEmailPort sendEmailPort;

    @Override
    public void handle(SendEmailVerificationCommand command) {
        // 1. 이메일 정규화
        String email = new Email(command.email()).value();

        // 2. 재발송 쿨다운 선점
        if (!acquireSendCooldownPort.tryAcquire(email)) {
            throw new EmailVerificationCooldownException();
        }

        // 3. 이메일 중복성 검사
        if (checkEmailDuplicatePort.existsByEmail(email)) {
            throw new EmailAlreadyExistsException();
        }

        // 4. 전송 횟수 제한 확인
        if (!checkDailySendLimitPort.tryConsume(email)) {
            throw new EmailVerificationDailyLimitExceededException();
        }

        // 5. email code를 생성한다.
        String code = generateVerificationCodePort.generate();
        // 6. email code를 해시화 한다.
        String hashedCode = verificationCodeHashPort.hash(code);

        try {
            // 7. email hash code 저장
            saveVerificationCodePort.save(email, hashedCode);
            // 8. email code 전송
            sendEmailPort.send(email, SUBJECT, buildBody(code));
        } catch (RuntimeException e) {
            // 저장이나 발송이 실패하면 코드와 쿨다운을 되돌린다
            deleteVerificationCodePort.delete(email);
            releaseSendCooldownPort.release(email);
            throw e;
        }
    }

    private String buildBody(String code) {
        return """
                아래 인증 코드를 입력해 주세요.
                %s
                본인이 요청하지 않았다면 이 메일을 무시해 주세요.
                """.formatted(code);
    }
}
