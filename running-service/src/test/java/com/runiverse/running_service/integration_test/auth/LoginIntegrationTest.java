package com.runiverse.running_service.integration_test.auth;

import com.runiverse.running_service.application.auth.command.login.LoginCommand;
import com.runiverse.running_service.application.auth.command.login.LoginHandler;
import com.runiverse.running_service.application.auth.command.login.LoginResult;
import com.runiverse.running_service.application.auth.command.signup.SignUpCommand;
import com.runiverse.running_service.application.auth.command.signup.SignUpHandler;
import com.runiverse.running_service.application.auth.exception.InvalidCredentialsException;
import com.runiverse.running_service.integration_test.IntegrationTestSupport;
import com.runiverse.running_service.support.LogCapture;
import ch.qos.logback.classic.Level;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("로그인 통합 테스트")
public class LoginIntegrationTest extends IntegrationTestSupport {

    private static final String EMAIL = "runner@runiverse.com";
    private static final String PASSWORD = "Password123!";
    private SignUpHandler signUpHandler;
    private LoginHandler loginHandler;
    private LogCapture loginLog;

    @BeforeEach
    void setUp() {
        signUpHandler = newSignUpHandler();
        loginHandler = new LoginHandler(
                userStore,
                passwordHasher,
                tokenProvider,
                tokenProvider,
                refreshTokenStore
        );
        loginLog = LogCapture.of(LoginHandler.class);
    }

    @AfterEach
    void tearDown() {
        loginLog.stop();
    }

    private UUID signUp() {
        return signUpHandler.handle(
                new SignUpCommand(issueVerificationTicket(EMAIL), PASSWORD)).userId();
    }

    @Test
    @DisplayName("가입한 계정으로 로그인하면 가입 때와 같은 userId와 토큰 두 개를 받는다")
    void loginSuccess() {
        // given
        UUID userId = signUp();
        // when
        LoginResult result = loginHandler.handle(new LoginCommand(EMAIL, PASSWORD));
        // then
        assertThat(result.userId()).isEqualTo(userId);
        assertThat(result.accessToken()).isNotBlank();
        assertThat(result.refreshToken()).isNotBlank();
        assertThat(result.accessToken()).isNotEqualTo(result.refreshToken());
    }

    @Test
    @DisplayName("refresh token은 원문이 아니라 해시로 저장된다")
    void loginStoresHashedRefreshToken() {
        // given
        UUID userId = signUp();
        // when
        LoginResult result = loginHandler.handle(new LoginCommand(EMAIL, PASSWORD));
        // then
        String storedHash = refreshTokenStore.loadById(userId).orElseThrow();
        assertThat(storedHash).isNotEqualTo(result.refreshToken());
        assertThat(tokenProvider.matches(result.refreshToken(), storedHash)).isTrue();
    }

    @Test
    @DisplayName("가입하지 않은 이메일로 로그인하면 InvalidCredentialsException이 발생한다")
    void loginWithUnknownEmail() {
        assertThatThrownBy(() -> loginHandler.handle(new LoginCommand(EMAIL, PASSWORD)))
                .isInstanceOf(InvalidCredentialsException.class);
        assertThat(refreshTokenStore.isEmpty()).isTrue();
    }

    @Test
    @DisplayName("비밀번호가 틀리면 이메일이 없을 때와 같은 예외가 발생한다")
    void loginWithWrongPassword() {
        // given - 가입 시 자동 로그인으로 이미 토큰이 하나 저장돼 있다
        UUID userId = signUp();
        String issuedAtSignUp = refreshTokenStore.loadById(userId).orElseThrow();
        // when & then — 예외를 구분하면 이메일 존재 여부가 새어나간다
        assertThatThrownBy(() -> loginHandler.handle(new LoginCommand(EMAIL, "WrongPassword1!")))
                .isInstanceOf(InvalidCredentialsException.class);
        // 실패한 로그인은 토큰을 새로 발급하지 않는다
        assertThat(refreshTokenStore.loadById(userId)).contains(issuedAtSignUp);
    }

    @Test
    @DisplayName("재로그인하면 새 refresh token이 발급되고 저장된 해시가 교체된다")
    void reLoginReplacesStoredRefreshToken() {
        // given
        UUID userId = signUp();
        LoginResult first = loginHandler.handle(new LoginCommand(EMAIL, PASSWORD));
        String firstHash = refreshTokenStore.loadById(userId).orElseThrow();
        // when
        LoginResult second = loginHandler.handle(new LoginCommand(EMAIL, PASSWORD));
        // then
        assertThat(second.refreshToken()).isNotEqualTo(first.refreshToken());
        String secondHash = refreshTokenStore.loadById(userId).orElseThrow();
        assertThat(secondHash).isNotEqualTo(firstHash);
        assertThat(tokenProvider.matches(first.refreshToken(), secondHash)).isFalse();
    }

    @Test
    @DisplayName("로그인에 성공하면 userId를 담아 성공 로그를 남긴다")
    void loginLogsSuccess() {
        // given
        UUID userId = signUp();
        // when
        loginHandler.handle(new LoginCommand(EMAIL, PASSWORD));
        // then
        assertThat(loginLog.messages(Level.INFO))
                .containsExactly("[인증] 로그인 성공 - userId=" + userId);
    }

    @Test
    @DisplayName("가입하지 않은 이메일이면 원인을 남기되 이메일 원문은 남기지 않는다")
    void loginWithUnknownEmailLogsFailure() {
        // when
        assertThatThrownBy(() -> loginHandler.handle(new LoginCommand(EMAIL, PASSWORD)))
                .isInstanceOf(InvalidCredentialsException.class);
        // then
        assertThat(loginLog.messages(Level.INFO))
                .containsExactly("[인증] 로그인 실패: 가입되지 않은 이메일");
    }

    @Test
    @DisplayName("비밀번호가 틀리면 응답과 달리 로그에는 원인을 구분해 userId와 함께 남긴다")
    void loginWithWrongPasswordLogsFailure() {
        // given
        UUID userId = signUp();
        String wrongPassword = "WrongPassword1!";
        // when
        assertThatThrownBy(() -> loginHandler.handle(new LoginCommand(EMAIL, wrongPassword)))
                .isInstanceOf(InvalidCredentialsException.class);
        // then -> 틀린 비밀번호는 대개 진짜 비밀번호의 오타라 남기지 않는다
        assertThat(loginLog.messages(Level.INFO))
                .containsExactly("[인증] 로그인 실패: 비밀번호 불일치 - userId=" + userId);
        assertThat(loginLog.messages(Level.INFO)).noneMatch(message -> message.contains(wrongPassword));
    }
}
