package com.runiverse.running_service.application.auth.command.emailverification;

import com.runiverse.running_service.application.auth.exception.EmailVerificationNotFoundException;
import com.runiverse.running_service.application.auth.exception.InvalidVerificationCodeException;
import com.runiverse.running_service.application.auth.exception.TooManyVerificationAttemptsException;
import com.runiverse.running_service.application.auth.port.in.VerifyEmailCodeUsecase;
import com.runiverse.running_service.application.auth.port.out.ConsumeVerificationAttemptPort;
import com.runiverse.running_service.application.auth.port.out.DeleteVerificationCodePort;
import com.runiverse.running_service.application.auth.port.out.GenerateVerificationTicketPort;
import com.runiverse.running_service.application.auth.port.out.SaveVerificationTicketPort;
import com.runiverse.running_service.application.auth.port.out.VerificationAttempt;
import com.runiverse.running_service.application.auth.port.out.VerificationCodeHashPort;
import com.runiverse.running_service.application.auth.port.out.VerificationTicketHashPort;
import com.runiverse.running_service.domain.user.vo.Email;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class VerifyEmailCodeHandler implements VerifyEmailCodeUsecase {

    private final ConsumeVerificationAttemptPort consumeVerificationAttemptPort;
    private final VerificationCodeHashPort verificationCodeHashPort;
    private final DeleteVerificationCodePort deleteVerificationCodePort;
    private final GenerateVerificationTicketPort generateVerificationTicketPort;
    private final SaveVerificationTicketPort saveVerificationTicketPort;
    private final VerificationTicketHashPort verificationTicketHashPort;

    @Override
    public VerifyEmailCodeResult handle(VerifyEmailCodeCommand command) {
        // 1. Email vo 정규화
        String email = new Email(command.email()).value();

        // 2. 시도 횟수를 소비하고 저장된 코드 상태를 가져온다
        VerificationAttempt attempt = consumeVerificationAttemptPort.consume(email);
        switch (attempt.status()) {
            case NOT_FOUND -> throw new EmailVerificationNotFoundException();
            case EXHAUSTED -> throw new TooManyVerificationAttemptsException();
            case AVAILABLE -> {
            }
        }

        // 3. 코드 대조. 시도 횟수는 이미 소비됐다
        if (!verificationCodeHashPort.matches(command.code(), attempt.hashedCode())) {
            throw new InvalidVerificationCodeException();
        }

        // 4. 맞은 코드는 재사용할 수 없도록 지운다.
        deleteVerificationCodePort.delete(email);

        // 5. 티켓 생성 후 해시 저장
        String ticket = generateVerificationTicketPort.generate();
        saveVerificationTicketPort.save(verificationTicketHashPort.hash(ticket), email);

        // 6. 원본 티켓 반환
        return new VerifyEmailCodeResult(ticket);
    }
}
