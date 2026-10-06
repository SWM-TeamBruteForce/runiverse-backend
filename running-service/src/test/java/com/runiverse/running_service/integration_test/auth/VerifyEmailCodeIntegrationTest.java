package com.runiverse.running_service.integration_test.auth;

import com.runiverse.running_service.application.auth.command.emailverification.SendEmailVerificationCommand;
import com.runiverse.running_service.application.auth.command.emailverification.SendEmailVerificationHandler;
import com.runiverse.running_service.application.auth.command.emailverification.VerifyEmailCodeCommand;
import com.runiverse.running_service.application.auth.command.emailverification.VerifyEmailCodeHandler;
import com.runiverse.running_service.application.auth.command.emailverification.VerifyEmailCodeResult;
import com.runiverse.running_service.application.auth.exception.EmailVerificationNotFoundException;
import com.runiverse.running_service.application.auth.exception.InvalidVerificationCodeException;
import com.runiverse.running_service.application.auth.exception.TooManyVerificationAttemptsException;
import com.runiverse.running_service.integration_test.IntegrationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("이메일 인증 코드 확인 통합 테스트")
public class VerifyEmailCodeIntegrationTest extends IntegrationTestSupport {

    private static final String EMAIL = "runner@runiverse.com";
    private static final String WRONG_CODE = "999999";

    private SendEmailVerificationHandler sendHandler;
    private VerifyEmailCodeHandler verifyHandler;

    @BeforeEach
    void setUp() {
        sendHandler = newSendEmailVerificationHandler();
        verifyHandler = newVerifyEmailCodeHandler();
    }

    // 발송을 거쳐야 코드가 저장되므로 실제 흐름 그대로 준비한다
    private String sendAndGetCode() {
        sendHandler.handle(new SendEmailVerificationCommand(EMAIL));
        return verificationCodeGenerator.lastGenerated();
    }

    @Test
    @DisplayName("코드가 맞으면 원문 티켓을 반환하고 저장소에는 티켓 해시만 남는다")
    void verifySuccess() {
        // given
        String code = sendAndGetCode();

        // when
        VerifyEmailCodeResult result = verifyHandler.handle(new VerifyEmailCodeCommand(EMAIL, code));

        // then
        assertThat(result.verificationTicket()).isNotBlank();
        assertThat(verificationTicketStore.size()).isEqualTo(1);
        // 티켓 해시로 조회하면 인증된 이메일이 나온다. 회원가입은 이 값만 믿는다
        assertThat(verificationTicketStore.consume(
                verificationTicketHasher.hash(result.verificationTicket()))).isEqualTo(EMAIL);
    }

    @Test
    @DisplayName("인증에 성공하면 코드는 삭제되어 같은 코드를 두 번 쓸 수 없다")
    void verifyDeletesCodeAfterSuccess() {
        // given
        String code = sendAndGetCode();
        verifyHandler.handle(new VerifyEmailCodeCommand(EMAIL, code));

        // when & then - 코드가 남아 있으면 티켓을 무한히 찍어낼 수 있다
        assertThat(emailVerificationStore.hasCode(EMAIL)).isFalse();
        assertThatThrownBy(() -> verifyHandler.handle(new VerifyEmailCodeCommand(EMAIL, code)))
                .isInstanceOf(EmailVerificationNotFoundException.class);
    }

    @Test
    @DisplayName("발송한 적 없는 이메일이면 EmailVerificationNotFoundException이 발생한다")
    void verifyWithoutSend() {
        // when & then
        assertThatThrownBy(() -> verifyHandler.handle(new VerifyEmailCodeCommand(EMAIL, "123456")))
                .isInstanceOf(EmailVerificationNotFoundException.class);
        assertThat(verificationTicketStore.size()).isZero();
    }

    @Test
    @DisplayName("코드가 틀리면 InvalidVerificationCodeException이 발생하고 시도 횟수가 소비된다")
    void verifyWithWrongCode() {
        // given
        sendAndGetCode();

        // when & then
        assertThatThrownBy(() -> verifyHandler.handle(new VerifyEmailCodeCommand(EMAIL, WRONG_CODE)))
                .isInstanceOf(InvalidVerificationCodeException.class);
        // 대조보다 소비가 먼저다. 틀린 코드로 무한히 두드릴 수 없어야 한다
        assertThat(emailVerificationStore.attempts(EMAIL)).isEqualTo(1);
        assertThat(verificationTicketStore.size()).isZero();
    }

    @Test
    @DisplayName("틀린 뒤에도 남은 횟수 안에서는 맞는 코드로 인증할 수 있다")
    void verifySucceedsAfterWrongAttempts() {
        // given
        String code = sendAndGetCode();
        assertThatThrownBy(() -> verifyHandler.handle(new VerifyEmailCodeCommand(EMAIL, WRONG_CODE)))
                .isInstanceOf(InvalidVerificationCodeException.class);

        // when
        VerifyEmailCodeResult result = verifyHandler.handle(new VerifyEmailCodeCommand(EMAIL, code));

        // then
        assertThat(result.verificationTicket()).isNotBlank();
    }

    @Test
    @DisplayName("시도 횟수를 모두 쓰면 TooManyVerificationAttemptsException이 발생한다")
    void verifyExhaustsAttempts() {
        // given
        String code = sendAndGetCode();
        for (int i = 0; i < MAX_ATTEMPTS; i++) {
            assertThatThrownBy(() -> verifyHandler.handle(new VerifyEmailCodeCommand(EMAIL, WRONG_CODE)))
                    .isInstanceOf(InvalidVerificationCodeException.class);
        }

        // when & then - 한도를 넘는 순간 맞는 코드를 넣어도 통과할 수 없다
        assertThatThrownBy(() -> verifyHandler.handle(new VerifyEmailCodeCommand(EMAIL, code)))
                .isInstanceOf(TooManyVerificationAttemptsException.class);
    }

    @Test
    @DisplayName("횟수 초과로 막히면 코드가 삭제되어 이후 요청은 NOT_FOUND가 된다")
    void verifyDeletesCodeWhenExhausted() {
        // given - 한도까지는 InvalidVerificationCode, 한 번 더 넘기면 TooMany가 난다
        String code = sendAndGetCode();
        for (int i = 0; i < MAX_ATTEMPTS; i++) {
            assertThatThrownBy(() -> verifyHandler.handle(new VerifyEmailCodeCommand(EMAIL, WRONG_CODE)))
                    .isInstanceOf(InvalidVerificationCodeException.class);
        }
        assertThatThrownBy(() -> verifyHandler.handle(new VerifyEmailCodeCommand(EMAIL, WRONG_CODE)))
                .isInstanceOf(TooManyVerificationAttemptsException.class);

        // when & then - 재발송을 받아야만 다시 시도할 수 있다
        assertThat(emailVerificationStore.hasCode(EMAIL)).isFalse();
        assertThatThrownBy(() -> verifyHandler.handle(new VerifyEmailCodeCommand(EMAIL, code)))
                .isInstanceOf(EmailVerificationNotFoundException.class);
    }

    @Test
    @DisplayName("대소문자가 다른 이메일로 확인해도 같은 인증 건으로 취급한다")
    void verifyNormalizesEmail() {
        // given
        String code = sendAndGetCode();

        // when
        VerifyEmailCodeResult result =
                verifyHandler.handle(new VerifyEmailCodeCommand("  RUNNER@Runiverse.COM  ", code));

        // then - 티켓에 담기는 이메일도 정규화된 값이어야 가입 이메일과 어긋나지 않는다
        assertThat(verificationTicketStore.consume(
                verificationTicketHasher.hash(result.verificationTicket()))).isEqualTo(EMAIL);
    }
}
