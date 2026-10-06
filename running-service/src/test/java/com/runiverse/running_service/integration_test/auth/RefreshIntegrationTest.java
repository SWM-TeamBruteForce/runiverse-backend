package com.runiverse.running_service.integration_test.auth;

import com.runiverse.running_service.application.auth.command.login.LoginCommand;
import com.runiverse.running_service.application.auth.command.login.LoginHandler;
import com.runiverse.running_service.application.auth.command.login.LoginResult;
import com.runiverse.running_service.application.auth.command.refresh.RefreshCommand;
import com.runiverse.running_service.application.auth.command.refresh.RefreshHandler;
import com.runiverse.running_service.application.auth.command.refresh.RefreshResult;
import com.runiverse.running_service.application.auth.command.signup.SignUpCommand;
import com.runiverse.running_service.application.auth.command.signup.SignUpHandler;
import com.runiverse.running_service.application.auth.command.signup.SignUpResult;
import com.runiverse.running_service.application.auth.exception.InvalidRefreshTokenException;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.integration_test.IntegrationTestSupport;
import com.runiverse.running_service.support.LogCapture;
import ch.qos.logback.classic.Level;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("토큰 재발급 통합 테스트")
public class RefreshIntegrationTest extends IntegrationTestSupport {

    private static final String EMAIL = "runner@runiverse.com";
    private static final String PASSWORD = "Password123!";
    private SignUpHandler signUpHandler;
    private LoginHandler loginHandler;
    private RefreshHandler refreshHandler;
    private LogCapture refreshLog;

    @BeforeEach
    void setUp() {
        signUpHandler = newSignUpHandler();
        loginHandler = new LoginHandler(
                userStore, passwordHasher, tokenProvider,
                tokenProvider, refreshTokenStore);
        refreshHandler = new RefreshHandler(
                tokenProvider,      // ParseRefreshTokenPort
                refreshTokenStore,  // LoadRefreshTokenPort
                tokenProvider,      // RefreshTokenHashPort
                refreshTokenStore,  // DeleteRefreshTokenPort
                tokenProvider,      // GenerateTokenPort
                refreshTokenStore   // SaveRefreshTokenHashPort
        );
        refreshLog = LogCapture.of(RefreshHandler.class);
    }

    @AfterEach
    void tearDown() {
        refreshLog.stop();
    }

    // 가입 -> 로그인까지 마친 상태를 만든다
    private LoginResult signUpAndLogin() {
        signUpHandler.handle(new SignUpCommand(issueVerificationTicket(EMAIL), PASSWORD));
        return loginHandler.handle(new LoginCommand(EMAIL, PASSWORD));
    }

    @Test
    @DisplayName("발급받은 refresh token으로 재발급하면 새 토큰 두 개를 받는다")
    void refreshSuccess() {
        // given
        LoginResult login = signUpAndLogin();
        // when
        RefreshResult result = refreshHandler.handle(new RefreshCommand(login.refreshToken()));
        // then
        assertThat(result.accessToken()).isNotBlank().isNotEqualTo(login.accessToken());
        assertThat(result.refreshToken()).isNotBlank().isNotEqualTo(login.refreshToken());
    }

    @Test
    @DisplayName("재발급하면 저장된 해시가 새 refresh token의 것으로 교체된다")
    void refreshReplacesStoredHash() {
        // given
        LoginResult login = signUpAndLogin();
        String beforeHash = refreshTokenStore.loadById(login.userId()).orElseThrow();
        // when
        RefreshResult result = refreshHandler.handle(new RefreshCommand(login.refreshToken()));
        // then
        String afterHash = refreshTokenStore.loadById(login.userId()).orElseThrow();
        assertThat(afterHash).isNotEqualTo(beforeHash);
        assertThat(tokenProvider.matches(result.refreshToken(), afterHash)).isTrue();
    }

    @Test
    @DisplayName("이미 사용한 refresh token을 다시 쓰면 탈취로 보고 저장된 토큰을 폐기한다")
    void reusingOldRefreshTokenRevokesEverything() {
        // given
        LoginResult login = signUpAndLogin();
        refreshHandler.handle(new RefreshCommand(login.refreshToken()));
        // when & then
        assertThatThrownBy(() -> refreshHandler.handle(new RefreshCommand(login.refreshToken())))
                .isInstanceOf(InvalidRefreshTokenException.class);
        // 정상 발급된 최신 토큰까지 함께 무효화된다
        assertThat(refreshTokenStore.loadById(login.userId())).isEmpty();
    }

    @Test
    @DisplayName("재발급받은 토큰으로 연속해서 재발급할 수 있다")
    void refreshChain() {
        // given
        LoginResult login = signUpAndLogin();
        // when
        RefreshResult first = refreshHandler.handle(new RefreshCommand(login.refreshToken()));
        RefreshResult second = refreshHandler.handle(new RefreshCommand(first.refreshToken()));
        // then
        assertThat(second.refreshToken()).isNotEqualTo(first.refreshToken());
        String storedHash = refreshTokenStore.loadById(login.userId()).orElseThrow();
        assertThat(tokenProvider.matches(second.refreshToken(), storedHash)).isTrue();
    }

    @Test
    @DisplayName("형식이 잘못된 토큰이면 InvalidRefreshTokenException이 발생한다")
    void refreshWithMalformedToken() {
        // given
        LoginResult login = signUpAndLogin();
        // when & then
        assertThatThrownBy(() -> refreshHandler.handle(new RefreshCommand("not-a-token")))
                .isInstanceOf(InvalidRefreshTokenException.class);
        // 남의 토큰이 아니므로 저장된 토큰은 그대로 살아 있다
        assertThat(refreshTokenStore.loadById(login.userId())).isPresent();
    }

    @Test
    @DisplayName("access token을 refresh token 자리에 넘기면 InvalidRefreshTokenException이 발생한다")
    void refreshWithAccessToken() {
        // given
        LoginResult login = signUpAndLogin();
        // when & then
        assertThatThrownBy(() -> refreshHandler.handle(new RefreshCommand(login.accessToken())))
                .isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    @DisplayName("저장소에 토큰이 없는 유저면 InvalidRefreshTokenException이 발생한다")
    void refreshWithoutStoredToken() {
        // given - 가입 시 자동 로그인으로 저장된 토큰까지 지워 저장 이력이 없는 상태를 만든다
        SignUpResult signUp = signUpHandler.handle(
                new SignUpCommand(issueVerificationTicket(EMAIL), PASSWORD));
        refreshTokenStore.delete(new UserId(signUp.userId()));
        String neverStored = signUp.refreshToken();
        // when & then
        assertThatThrownBy(() -> refreshHandler.handle(new RefreshCommand(neverStored)))
                .isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    @DisplayName("재발급에 성공하면 userId를 담아 성공 로그를 남긴다")
    void refreshLogsSuccess() {
        // given
        LoginResult login = signUpAndLogin();
        // when
        refreshHandler.handle(new RefreshCommand(login.refreshToken()));
        // then
        assertThat(refreshLog.messages(Level.INFO))
                .containsExactly("[인증] 토큰 재발급 성공 - userId=" + login.userId());
    }

    @Test
    @DisplayName("토큰 검증에 실패하면 소유자를 알 수 없어 원인만 남기고 토큰 원문은 남기지 않는다")
    void refreshWithMalformedTokenLogsFailure() {
        // when
        assertThatThrownBy(() -> refreshHandler.handle(new RefreshCommand("not-a-token")))
                .isInstanceOf(InvalidRefreshTokenException.class);
        // then
        assertThat(refreshLog.messages(Level.INFO))
                .containsExactly("[인증] 토큰 재발급 실패: 토큰 검증 실패");
        assertThat(refreshLog.messages(Level.INFO)).noneMatch(message -> message.contains("not-a-token"));
    }

    @Test
    @DisplayName("저장된 토큰이 없으면 userId를 담아 실패 로그를 남긴다")
    void refreshWithoutStoredTokenLogsFailure() {
        // given
        SignUpResult signUp = signUpHandler.handle(
                new SignUpCommand(issueVerificationTicket(EMAIL), PASSWORD));
        refreshTokenStore.delete(new UserId(signUp.userId()));
        // when
        assertThatThrownBy(() -> refreshHandler.handle(new RefreshCommand(signUp.refreshToken())))
                .isInstanceOf(InvalidRefreshTokenException.class);
        // then
        assertThat(refreshLog.messages(Level.INFO))
                .containsExactly("[인증] 토큰 재발급 실패: 저장된 토큰 없음 - userId=" + signUp.userId());
    }

    @Test
    @DisplayName("저장된 토큰과 다르면 탈취나 중복 요청을 의심해 WARN으로 남긴다")
    void reusingOldRefreshTokenLogsWarn() {
        // given
        LoginResult login = signUpAndLogin();
        refreshHandler.handle(new RefreshCommand(login.refreshToken()));
        // when
        assertThatThrownBy(() -> refreshHandler.handle(new RefreshCommand(login.refreshToken())))
                .isInstanceOf(InvalidRefreshTokenException.class);
        // then -> 사용자 실수가 아니라 따로 모아 봐야 하는 신호라 INFO와 섞지 않는다
        assertThat(refreshLog.messages(Level.WARN))
                .containsExactly("[인증] 토큰 재발급 실패: 저장된 토큰과 불일치 - userId=" + login.userId());
        assertThat(refreshLog.messages(Level.INFO))
                .containsExactly("[인증] 토큰 재발급 성공 - userId=" + login.userId());
    }
}
