package com.runiverse.running_service.integration_test.auth;

import com.runiverse.running_service.application.auth.command.signup.SignUpCommand;
import com.runiverse.running_service.application.auth.command.signup.SignUpHandler;
import com.runiverse.running_service.application.auth.command.signup.SignUpResult;
import com.runiverse.running_service.application.auth.command.signup.SignUpUserRegistrar;
import com.runiverse.running_service.application.auth.exception.EmailAlreadyExistsException;
import com.runiverse.running_service.application.auth.exception.EmailNotVerifiedException;
import com.runiverse.running_service.domain.user.User;
import com.runiverse.running_service.integration_test.IntegrationTestSupport;
import com.runiverse.running_service.support.LogCapture;
import ch.qos.logback.classic.Level;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("회원가입 통합 테스트")
public class SignUpIntegrationTest extends IntegrationTestSupport {

    private static final String EMAIL = "runner@runiverse.com";
    private static final String PASSWORD = "Password123!";
    private SignUpHandler signUpHandler;
    private LogCapture handlerLog;
    private LogCapture registrarLog;

    @BeforeEach
    void setUp() {
        signUpHandler = newSignUpHandler();
        handlerLog = LogCapture.of(SignUpHandler.class);
        registrarLog = LogCapture.of(SignUpUserRegistrar.class);
    }

    @AfterEach
    void tearDown() {
        handlerLog.stop();
        registrarLog.stop();
    }

    @Test
    @DisplayName("회원가입에 성공하면 UUIDv7 userId를 반환하고 티켓에 담긴 이메일로 유저가 저장된다")
    void signUpSuccess() {
        // given
        String ticket = issueVerificationTicket(EMAIL);
        // when
        SignUpResult result = signUpHandler.handle(new SignUpCommand(ticket, PASSWORD));
        // then
        assertThat(result.userId()).isNotNull();
        assertThat(result.userId().version()).isEqualTo(7);
        assertThat(userStore.size()).isEqualTo(1);
        User saved = userStore.findById(result.userId()).orElseThrow();
        // 요청에는 이메일이 없다. 이 값은 오직 티켓에서만 나온다
        assertThat(saved.getEmail().value()).isEqualTo(EMAIL);
    }

    @Test
    @DisplayName("가입에 성공하면 티켓은 소비되어 저장소에서 사라진다")
    void signUpConsumesTicket() {
        // given
        String ticket = issueVerificationTicket(EMAIL);
        // when
        signUpHandler.handle(new SignUpCommand(ticket, PASSWORD));
        // then
        assertThat(verificationTicketStore.size()).isZero();
    }

    @Test
    @DisplayName("비밀번호는 평문이 아니라 해시로 저장된다")
    void signUpStoresHashedPassword() {
        // when
        SignUpResult result = signUpHandler.handle(
                new SignUpCommand(issueVerificationTicket(EMAIL), PASSWORD));
        // then
        User saved = userStore.findById(result.userId()).orElseThrow();
        String storedHash = saved.getPasswordHash().value();
        assertThat(storedHash).isNotEqualTo(PASSWORD);
        assertThat(passwordHasher.matches(PASSWORD, storedHash)).isTrue();
        assertThat(passwordHasher.matches("WrongPassword1!", storedHash)).isFalse();
    }

    @Test
    @DisplayName("이미 가입된 이메일이면 EmailAlreadyExistsException이 발생한다")
    void signUpWithDuplicateEmail() {
        // given
        signUpHandler.handle(new SignUpCommand(issueVerificationTicket(EMAIL), PASSWORD));
        // 티켓은 1회용이라 두 번째 시도에는 새로 발급받아야 한다
        String secondTicket = issueVerificationTicket(EMAIL);
        // when & then
        assertThatThrownBy(() -> signUpHandler.handle(new SignUpCommand(secondTicket, "Another123!")))
                .isInstanceOf(EmailAlreadyExistsException.class);
        // 중복 요청이 기존 데이터를 덮어쓰지 않는다
        assertThat(userStore.size()).isEqualTo(1);
        // 실패했으므로 토큰도 첫 가입 때 받은 것 하나뿐이다
        assertThat(refreshTokenStore.size()).isEqualTo(1);
    }

    @Test
    @DisplayName("중복 이메일로 실패해도 티켓은 이미 소비되어 되돌아오지 않는다")
    void signUpConsumesTicketEvenWhenDuplicate() {
        // given
        signUpHandler.handle(new SignUpCommand(issueVerificationTicket(EMAIL), PASSWORD));
        String secondTicket = issueVerificationTicket(EMAIL);
        // when
        assertThatThrownBy(() -> signUpHandler.handle(new SignUpCommand(secondTicket, PASSWORD)))
                .isInstanceOf(EmailAlreadyExistsException.class);
        // then - Redis 삭제는 트랜잭션 롤백 대상이 아니다. 재시도하려면 이메일 인증부터 다시 해야 한다
        assertThat(verificationTicketStore.size()).isZero();
    }

    @Test
    @DisplayName("서로 다른 이메일로 가입하면 각각 다른 userId를 받는다")
    void signUpMultipleUsers() {
        SignUpResult first = signUpHandler.handle(
                new SignUpCommand(issueVerificationTicket(EMAIL), PASSWORD));
        SignUpResult second = signUpHandler.handle(
                new SignUpCommand(issueVerificationTicket("other@runiverse.com"), PASSWORD));
        assertThat(second.userId()).isNotEqualTo(first.userId());
        assertThat(userStore.size()).isEqualTo(2);
    }

    @Test
    @DisplayName("가입에 성공하면 자동 로그인되어 토큰 두 개를 함께 받는다")
    void signUpIssuesTokens() {
        // when
        SignUpResult result = signUpHandler.handle(
                new SignUpCommand(issueVerificationTicket(EMAIL), PASSWORD));
        // then
        assertThat(result.accessToken()).isNotBlank();
        assertThat(result.refreshToken()).isNotBlank();
        assertThat(result.accessToken()).isNotEqualTo(result.refreshToken());
    }

    @Test
    @DisplayName("refresh token은 원문이 아니라 해시로 저장된다")
    void signUpStoresHashedRefreshToken() {
        // when
        SignUpResult result = signUpHandler.handle(
                new SignUpCommand(issueVerificationTicket(EMAIL), PASSWORD));
        // then
        String storedHash = refreshTokenStore.loadById(result.userId()).orElseThrow();
        assertThat(storedHash).isNotEqualTo(result.refreshToken());
        assertThat(tokenProvider.matches(result.refreshToken(), storedHash)).isTrue();
    }

    @Test
    @DisplayName("발급받은 적 없는 티켓이면 EmailNotVerifiedException이 발생하고 저장되지 않는다")
    void signUpWithUnknownTicket() {
        assertThatThrownBy(() -> signUpHandler.handle(new SignUpCommand("not-a-ticket", PASSWORD)))
                .isInstanceOf(EmailNotVerifiedException.class);
        assertThat(userStore.size()).isZero();
        // 유저가 없으니 토큰도 발급되지 않는다
        assertThat(refreshTokenStore.isEmpty()).isTrue();
    }

    @Test
    @DisplayName("같은 티켓을 두 번 쓰면 두 번째는 EmailNotVerifiedException이 발생한다")
    void signUpWithUsedTicket() {
        // given
        String ticket = issueVerificationTicket(EMAIL);
        signUpHandler.handle(new SignUpCommand(ticket, PASSWORD));
        // when & then - 티켓은 1회용이다
        assertThatThrownBy(() -> signUpHandler.handle(new SignUpCommand(ticket, PASSWORD)))
                .isInstanceOf(EmailNotVerifiedException.class);
        assertThat(userStore.size()).isEqualTo(1);
    }

    @Test
    @DisplayName("가입에 성공하면 userId를 담아 성공 로그를 남긴다")
    void signUpLogsSuccess() {
        // when
        SignUpResult result = signUpHandler.handle(
                new SignUpCommand(issueVerificationTicket(EMAIL), PASSWORD));
        // then
        assertThat(handlerLog.messages(Level.INFO))
                .containsExactly("[인증] 회원가입 성공 - userId=" + result.userId());
    }

    @Test
    @DisplayName("티켓이 없으면 실패 원인을 남기고 성공 로그는 남기지 않는다")
    void signUpWithUnknownTicketLogsFailure() {
        // when
        assertThatThrownBy(() -> signUpHandler.handle(new SignUpCommand("not-a-ticket", PASSWORD)))
                .isInstanceOf(EmailNotVerifiedException.class);
        // then -> 티켓은 토큰류라 로그에 남기지 않는다
        assertThat(handlerLog.messages(Level.INFO))
                .containsExactly("[인증] 회원가입 실패: 인증 티켓 없음 또는 만료");
    }

    @Test
    @DisplayName("이미 가입된 이메일이면 실패 원인을 남기되 이메일 원문은 남기지 않는다")
    void signUpWithDuplicateEmailLogsFailure() {
        // given
        signUpHandler.handle(new SignUpCommand(issueVerificationTicket(EMAIL), PASSWORD));
        String secondTicket = issueVerificationTicket(EMAIL);
        // when
        assertThatThrownBy(() -> signUpHandler.handle(new SignUpCommand(secondTicket, PASSWORD)))
                .isInstanceOf(EmailAlreadyExistsException.class);
        // then
        assertThat(registrarLog.messages(Level.INFO))
                .containsExactly("[인증] 회원가입 실패: 이미 가입된 이메일");
        // 성공 로그는 첫 가입 한 번뿐이다
        assertThat(handlerLog.messages(Level.INFO)).hasSize(1);
        assertThat(registrarLog.messages(Level.INFO)).noneMatch(message -> message.contains(EMAIL));
    }
}
