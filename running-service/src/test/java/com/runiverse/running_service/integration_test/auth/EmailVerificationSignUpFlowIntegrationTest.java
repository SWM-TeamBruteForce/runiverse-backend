package com.runiverse.running_service.integration_test.auth;

import com.runiverse.running_service.application.auth.command.emailverification.SendEmailVerificationCommand;
import com.runiverse.running_service.application.auth.command.emailverification.SendEmailVerificationHandler;
import com.runiverse.running_service.application.auth.command.emailverification.VerifyEmailCodeCommand;
import com.runiverse.running_service.application.auth.command.emailverification.VerifyEmailCodeHandler;
import com.runiverse.running_service.application.auth.command.emailverification.VerifyEmailCodeResult;
import com.runiverse.running_service.application.auth.command.signup.SignUpCommand;
import com.runiverse.running_service.application.auth.command.signup.SignUpHandler;
import com.runiverse.running_service.application.auth.command.signup.SignUpResult;
import com.runiverse.running_service.application.auth.exception.EmailNotVerifiedException;
import com.runiverse.running_service.domain.user.User;
import com.runiverse.running_service.integration_test.IntegrationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("이메일 인증 -> 회원가입 전체 흐름 통합 테스트")
public class EmailVerificationSignUpFlowIntegrationTest extends IntegrationTestSupport {

    private static final String EMAIL = "runner@runiverse.com";
    private static final String PASSWORD = "Password123!";

    private SendEmailVerificationHandler sendHandler;
    private VerifyEmailCodeHandler verifyHandler;
    private SignUpHandler signUpHandler;

    @BeforeEach
    void setUp() {
        sendHandler = newSendEmailVerificationHandler();
        verifyHandler = newVerifyEmailCodeHandler();
        signUpHandler = newSignUpHandler();
    }

    @Test
    @DisplayName("발송 -> 확인 -> 가입이 한 흐름으로 이어지고 이메일은 티켓에서만 나온다")
    void fullFlow() {
        // given - 1. 인증 메일을 받는다
        sendHandler.handle(new SendEmailVerificationCommand(EMAIL));
        String code = verificationCodeGenerator.lastGenerated();

        // 2. 코드를 확인해 티켓을 받는다
        VerifyEmailCodeResult verified = verifyHandler.handle(new VerifyEmailCodeCommand(EMAIL, code));

        // when - 3. 티켓으로 가입한다. 요청에는 이메일이 아예 없다
        SignUpResult signedUp = signUpHandler.handle(
                new SignUpCommand(verified.verificationTicket(), PASSWORD));

        // then
        User saved = userStore.findById(signedUp.userId()).orElseThrow();
        assertThat(saved.getEmail().value()).isEqualTo(EMAIL);
        // 가입과 동시에 자동 로그인된다
        assertThat(signedUp.accessToken()).isNotBlank();
        assertThat(signedUp.refreshToken()).isNotBlank();
        // 티켓과 코드는 모두 소진돼 재사용할 수 없다
        assertThat(verificationTicketStore.size()).isZero();
        assertThat(emailVerificationStore.hasCode(EMAIL)).isFalse();
    }

    @Test
    @DisplayName("인증 코드 확인을 건너뛰고 받은 적 없는 티켓으로는 가입할 수 없다")
    void signUpWithoutVerification() {
        // given - 메일만 보내고 확인 단계를 생략한다
        sendHandler.handle(new SendEmailVerificationCommand(EMAIL));

        // when & then
        assertThatThrownBy(() -> signUpHandler.handle(new SignUpCommand("forged-ticket", PASSWORD)))
                .isInstanceOf(EmailNotVerifiedException.class);
        assertThat(userStore.size()).isZero();
    }

    @Test
    @DisplayName("인증한 이메일의 대소문자가 달라도 가입 이메일은 하나로 모인다")
    void flowNormalizesEmail() {
        // given
        sendHandler.handle(new SendEmailVerificationCommand("RUNNER@Runiverse.COM"));
        VerifyEmailCodeResult verified = verifyHandler.handle(
                new VerifyEmailCodeCommand("runner@RUNIVERSE.com", verificationCodeGenerator.lastGenerated()));

        // when
        SignUpResult signedUp = signUpHandler.handle(
                new SignUpCommand(verified.verificationTicket(), PASSWORD));

        // then
        assertThat(userStore.findById(signedUp.userId()).orElseThrow()
                .getEmail().value()).isEqualTo(EMAIL);
    }
}
