package com.runiverse.running_service.integration_test.auth;

import com.runiverse.running_service.application.auth.command.login.LoginCommand;
import com.runiverse.running_service.application.auth.command.login.LoginHandler;
import com.runiverse.running_service.application.auth.command.login.LoginResult;
import com.runiverse.running_service.application.auth.command.logout.LogoutCommand;
import com.runiverse.running_service.application.auth.command.logout.LogoutHandler;
import com.runiverse.running_service.application.auth.command.refresh.RefreshCommand;
import com.runiverse.running_service.application.auth.command.refresh.RefreshHandler;
import com.runiverse.running_service.application.auth.command.signup.SignUpCommand;
import com.runiverse.running_service.application.auth.command.signup.SignUpHandler;
import com.runiverse.running_service.application.auth.exception.InvalidRefreshTokenException;
import com.runiverse.running_service.integration_test.IntegrationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("로그아웃 통합 테스트")
public class LogoutIntegrationTest extends IntegrationTestSupport {

    private static final String EMAIL = "runner@runiverse.com";
    private static final String PASSWORD = "Password123!";
    private static final String ACCESS_TOKEN_ID = "jti-access-1";
    private SignUpHandler signUpHandler;
    private LoginHandler loginHandler;
    private RefreshHandler refreshHandler;
    private LogoutHandler logoutHandler;

    @BeforeEach
    void setUp() {
        signUpHandler = newSignUpHandler();
        loginHandler = new LoginHandler(
                userStore, passwordHasher, tokenProvider,
                tokenProvider, refreshTokenStore);
        refreshHandler = new RefreshHandler(
                tokenProvider, refreshTokenStore, tokenProvider,
                refreshTokenStore, tokenProvider, refreshTokenStore);
        logoutHandler = new LogoutHandler(
                refreshTokenStore,     // DeleteRefreshTokenPort
                accessTokenBlacklist   // BlockAccessTokenPort
        );
    }

    private LoginResult signUpAndLogin() {
        signUpHandler.handle(new SignUpCommand(issueVerificationTicket(EMAIL), PASSWORD));
        return loginHandler.handle(new LoginCommand(EMAIL, PASSWORD));
    }

    @Test
    @DisplayName("로그아웃하면 refresh token이 지워지고 access token이 블랙리스트에 오른다")
    void logoutSuccess() {
        // given
        LoginResult login = signUpAndLogin();
        // when
        logoutHandler.handle(new LogoutCommand(login.userId(), ACCESS_TOKEN_ID));
        // then
        assertThat(refreshTokenStore.loadById(login.userId())).isEmpty();
        assertThat(accessTokenBlacklist.isBlocked(ACCESS_TOKEN_ID)).isTrue();
    }

    @Test
    @DisplayName("로그아웃한 뒤에는 기존 refresh token으로 재발급할 수 없다")
    void refreshAfterLogoutFails() {
        // given
        LoginResult login = signUpAndLogin();
        logoutHandler.handle(new LogoutCommand(login.userId(), ACCESS_TOKEN_ID));
        // when & then
        assertThatThrownBy(() -> refreshHandler.handle(new RefreshCommand(login.refreshToken())))
                .isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    @DisplayName("로그아웃 후 다시 로그인하면 새 refresh token이 발급된다")
    void loginAgainAfterLogout() {
        // given
        LoginResult first = signUpAndLogin();
        logoutHandler.handle(new LogoutCommand(first.userId(), ACCESS_TOKEN_ID));
        // when
        LoginResult second = loginHandler.handle(new LoginCommand(EMAIL, PASSWORD));
        // then
        assertThat(second.userId()).isEqualTo(first.userId());
        assertThat(second.refreshToken()).isNotEqualTo(first.refreshToken());

        String storedHash = refreshTokenStore.loadById(second.userId()).orElseThrow();
        assertThat(tokenProvider.matches(second.refreshToken(), storedHash)).isTrue();
    }

    @Test
    @DisplayName("이전 access token은 재로그인해도 블랙리스트에 남는다")
    void blacklistSurvivesReLogin() {
        // given
        LoginResult first = signUpAndLogin();
        logoutHandler.handle(new LogoutCommand(first.userId(), ACCESS_TOKEN_ID));
        // when
        loginHandler.handle(new LoginCommand(EMAIL, PASSWORD));
        // then
        assertThat(accessTokenBlacklist.isBlocked(ACCESS_TOKEN_ID)).isTrue();
    }

    @Test
    @DisplayName("이미 로그아웃한 상태에서 다시 로그아웃해도 예외가 발생하지 않는다")
    void logoutIsIdempotent() {
        // given
        LoginResult login = signUpAndLogin();
        logoutHandler.handle(new LogoutCommand(login.userId(), ACCESS_TOKEN_ID));
        // when & then
        assertThatCode(() -> logoutHandler.handle(new LogoutCommand(login.userId(), ACCESS_TOKEN_ID)))
                .doesNotThrowAnyException();
        assertThat(refreshTokenStore.loadById(login.userId())).isEmpty();
    }

    @Test
    @DisplayName("로그인한 적 없는 유저를 로그아웃해도 예외 없이 access token만 차단한다")
    void logoutWithoutLogin() {
        // given
        UUID userId = signUpHandler.handle(
                new SignUpCommand(issueVerificationTicket(EMAIL), PASSWORD)).userId();
        // when & then
        assertThatCode(() -> logoutHandler.handle(new LogoutCommand(userId, ACCESS_TOKEN_ID)))
                .doesNotThrowAnyException();
        assertThat(accessTokenBlacklist.isBlocked(ACCESS_TOKEN_ID)).isTrue();
    }

    @Test
    @DisplayName("한 유저의 로그아웃이 다른 유저의 세션을 건드리지 않는다")
    void logoutDoesNotAffectOtherUsers() {
        // given
        LoginResult mine = signUpAndLogin();
        signUpHandler.handle(
                new SignUpCommand(issueVerificationTicket("other@runiverse.com"), PASSWORD));
        LoginResult others = loginHandler.handle(new LoginCommand("other@runiverse.com", PASSWORD));
        // when
        logoutHandler.handle(new LogoutCommand(mine.userId(), ACCESS_TOKEN_ID));
        // then
        assertThat(refreshTokenStore.loadById(mine.userId())).isEmpty();
        assertThat(refreshTokenStore.loadById(others.userId())).isPresent();
        assertThat(accessTokenBlacklist.isBlocked("jti-access-other")).isFalse();
    }
}
