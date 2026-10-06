package com.runiverse.running_service.integration_test.auth;

import com.runiverse.running_service.application.auth.command.emailverification.SendEmailVerificationCommand;
import com.runiverse.running_service.application.auth.command.emailverification.SendEmailVerificationHandler;
import com.runiverse.running_service.application.auth.exception.EmailSendFailedException;
import com.runiverse.running_service.application.auth.exception.EmailVerificationCooldownException;
import com.runiverse.running_service.application.auth.exception.EmailVerificationDailyLimitExceededException;
import com.runiverse.running_service.domain.user.exception.InvalidEmailFormatException;
import com.runiverse.running_service.integration_test.IntegrationTestSupport;
import com.runiverse.running_service.integration_test.fake.FakeEmailSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("이메일 인증 코드 발송 통합 테스트")
public class SendEmailVerificationIntegrationTest extends IntegrationTestSupport {

    private static final String EMAIL = "runner@runiverse.com";
    private static final String SUBJECT = "[Runiverse] 이메일 인증 코드";

    private SendEmailVerificationHandler handler;

    @BeforeEach
    void setUp() {
        handler = newSendEmailVerificationHandler();
    }

    @Test
    @DisplayName("발송에 성공하면 메일에는 원문 코드가, 저장소에는 해시만 남는다")
    void sendSuccess() {
        // when
        handler.handle(new SendEmailVerificationCommand(EMAIL));

        // then
        String code = verificationCodeGenerator.lastGenerated();
        FakeEmailSender.SentEmail mail = emailSender.last();
        assertThat(emailSender.size()).isEqualTo(1);
        assertThat(mail.to()).isEqualTo(EMAIL);
        assertThat(mail.subject()).isEqualTo(SUBJECT);
        assertThat(mail.body()).contains(code);
        // 저장된 값이 원문이면 Redis가 털렸을 때 그대로 인증이 뚫린다
        assertThat(emailVerificationStore.storedHash(EMAIL))
                .isNotEqualTo(code)
                .isEqualTo(verificationCodeHasher.hash(code));
    }

    @Test
    @DisplayName("발송에 성공하면 쿨다운이 잡히고 일일 카운트가 1 올라간다")
    void sendAcquiresCooldownAndCountsDaily() {
        // when
        handler.handle(new SendEmailVerificationCommand(EMAIL));

        // then
        assertThat(emailVerificationStore.hasCooldown(EMAIL)).isTrue();
        assertThat(emailVerificationStore.dailyCount(EMAIL)).isEqualTo(1);
    }

    @Test
    @DisplayName("쿨다운 중에 다시 요청하면 EmailVerificationCooldownException이 발생하고 메일은 나가지 않는다")
    void sendDuringCooldown() {
        // given
        handler.handle(new SendEmailVerificationCommand(EMAIL));

        // when & then
        assertThatThrownBy(() -> handler.handle(new SendEmailVerificationCommand(EMAIL)))
                .isInstanceOf(EmailVerificationCooldownException.class);
        assertThat(emailSender.size()).isEqualTo(1);
        // 쿨다운에 막혔으므로 일일 한도는 소비되지 않는다
        assertThat(emailVerificationStore.dailyCount(EMAIL)).isEqualTo(1);
    }

    @Test
    @DisplayName("하루 발송 한도를 넘기면 EmailVerificationDailyLimitExceededException이 발생한다")
    void sendExceedsDailyLimit() {
        // given - 쿨다운은 발송마다 새로 잡히므로 풀어 주면서 한도까지 채운다
        for (int i = 0; i < DAILY_LIMIT; i++) {
            handler.handle(new SendEmailVerificationCommand(EMAIL));
            emailVerificationStore.release(EMAIL);
        }

        // when & then
        assertThatThrownBy(() -> handler.handle(new SendEmailVerificationCommand(EMAIL)))
                .isInstanceOf(EmailVerificationDailyLimitExceededException.class);
        assertThat(emailSender.size()).isEqualTo(DAILY_LIMIT);
    }

    @Test
    @DisplayName("메일 발송이 실패하면 저장한 코드와 쿨다운을 되돌린다")
    void sendRollsBackWhenMailFails() {
        // given
        emailSender.failWith(new EmailSendFailedException());

        // when
        assertThatThrownBy(() -> handler.handle(new SendEmailVerificationCommand(EMAIL)))
                .isInstanceOf(EmailSendFailedException.class);

        // then - 코드나 쿨다운이 남으면 사용자가 재시도할 방법이 없다
        assertThat(emailVerificationStore.hasCode(EMAIL)).isFalse();
        assertThat(emailVerificationStore.hasCooldown(EMAIL)).isFalse();
        // 일일 카운트는 되돌리지 않는다. 무한 재시도로 발송량을 늘리지 못하게 하기 위해서다
        assertThat(emailVerificationStore.dailyCount(EMAIL)).isEqualTo(1);
    }

    @Test
    @DisplayName("이메일은 소문자로 정규화된 뒤 저장되고 발송된다")
    void sendNormalizesEmail() {
        // when
        handler.handle(new SendEmailVerificationCommand("  RUNNER@Runiverse.COM  "));

        // then - 대소문자를 바꿔 보내도 같은 계정으로 취급돼야 쿨다운과 한도가 뚫리지 않는다
        assertThat(emailSender.last().to()).isEqualTo(EMAIL);
        assertThat(emailVerificationStore.hasCode(EMAIL)).isTrue();
        assertThat(emailVerificationStore.hasCooldown(EMAIL)).isTrue();
    }

    @Test
    @DisplayName("재발송하면 이전 코드와 시도 횟수를 덮어쓴다")
    void resendOverwritesPreviousCode() {
        // given - 한 번 발송하고 시도 횟수를 소비해 둔다
        handler.handle(new SendEmailVerificationCommand(EMAIL));
        String firstCode = verificationCodeGenerator.lastGenerated();
        emailVerificationStore.consume(EMAIL);
        assertThat(emailVerificationStore.attempts(EMAIL)).isEqualTo(1);
        emailVerificationStore.release(EMAIL);

        // when
        handler.handle(new SendEmailVerificationCommand(EMAIL));

        // then
        String secondCode = verificationCodeGenerator.lastGenerated();
        assertThat(secondCode).isNotEqualTo(firstCode);
        assertThat(emailVerificationStore.storedHash(EMAIL))
                .isEqualTo(verificationCodeHasher.hash(secondCode));
        // 새 코드를 받았으면 시도 횟수도 처음부터 다시 세야 한다
        assertThat(emailVerificationStore.attempts(EMAIL)).isZero();
    }

    @Test
    @DisplayName("이메일 형식이 잘못되면 저장소를 건드리기 전에 실패한다")
    void sendWithInvalidEmail() {
        // when & then
        assertThatThrownBy(() -> handler.handle(new SendEmailVerificationCommand("not-an-email")))
                .isInstanceOf(InvalidEmailFormatException.class);
        // 형식 검증이 쿨다운 선점보다 앞에 있어야 쓰레기 키가 쌓이지 않는다
        assertThat(emailVerificationStore.hasCooldown("not-an-email")).isFalse();
        assertThat(emailVerificationStore.dailyCount("not-an-email")).isZero();
        assertThat(emailSender.isEmpty()).isTrue();
    }
}
