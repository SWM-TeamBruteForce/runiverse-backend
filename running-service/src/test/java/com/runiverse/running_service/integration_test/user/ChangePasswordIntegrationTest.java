package com.runiverse.running_service.integration_test.user;

import com.github.f4b6a3.uuid.UuidCreator;
import com.runiverse.running_service.application.auth.command.login.LoginCommand;
import com.runiverse.running_service.application.auth.command.login.LoginHandler;
import com.runiverse.running_service.application.auth.command.signup.SignUpCommand;
import com.runiverse.running_service.application.auth.command.signup.SignUpHandler;
import com.runiverse.running_service.application.auth.exception.InvalidCredentialsException;
import com.runiverse.running_service.application.user.command.password.ChangePasswordCommand;
import com.runiverse.running_service.application.user.command.password.ChangePasswordHandler;
import com.runiverse.running_service.application.user.exception.InvalidCurrentPasswordException;
import com.runiverse.running_service.application.user.exception.PasswordNotSetException;
import com.runiverse.running_service.application.user.exception.UserNotFoundException;
import com.runiverse.running_service.domain.user.User;
import com.runiverse.running_service.domain.user.vo.PasswordHash;
import com.runiverse.running_service.domain.user.vo.Provider;
import com.runiverse.running_service.integration_test.IntegrationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("비밀번호 변경 통합 테스트")
public class ChangePasswordIntegrationTest extends IntegrationTestSupport {

    private static final String EMAIL = "runner@runiverse.com";
    private static final String OAUTH_EMAIL = "social@runiverse.com";
    private static final String PASSWORD = "Password123!";
    private static final String NEW_PASSWORD = "NewPassword123!";
    private static final String KAKAO_ID = "3812345678";

    private SignUpHandler signUpHandler;
    private LoginHandler loginHandler;
    private ChangePasswordHandler changePasswordHandler;

    @BeforeEach
    void setUp() {
        signUpHandler = newSignUpHandler();
        loginHandler = new LoginHandler(
                userStore,          // LoadUserByEmailPort
                passwordHasher,     // PasswordHashPort
                tokenProvider,      // GenerateTokenPort
                tokenProvider,      // RefreshTokenHashPort
                refreshTokenStore   // SaveRefreshTokenHashPort
        );
        changePasswordHandler = new ChangePasswordHandler(
                userStore,       // LoadUserByIdPort
                passwordHasher,  // PasswordHashPort
                userStore        // UpdatePasswordPort
        );
    }

    private UUID signUp() {
        return signUpHandler.handle(
                new SignUpCommand(issueVerificationTicket(EMAIL), PASSWORD)).userId();
    }

    // 소셜 전용 유저는 해시가 비어 있어 가입 흐름을 태울 수 없다
    private UUID oauthOnlyUser() {
        UUID userId = UuidCreator.getTimeOrderedEpoch();
        userStore.save(User.registerWithOauth(userId, OAUTH_EMAIL, Provider.KAKAO, KAKAO_ID));
        return userId;
    }

    @Test
    @DisplayName("비밀번호를 바꾸면 새 비밀번호로 로그인되고 옛 비밀번호는 막힌다")
    void changePasswordSuccess() {
        // given
        UUID userId = signUp();

        // when
        changePasswordHandler.handle(new ChangePasswordCommand(userId, PASSWORD, NEW_PASSWORD));

        // then -> 저장소가 아니라 로그인으로 확인한다. 실제로 바뀌지 않으면 여기서 갈린다
        assertThatCode(() -> loginHandler.handle(new LoginCommand(EMAIL, NEW_PASSWORD)))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> loginHandler.handle(new LoginCommand(EMAIL, PASSWORD)))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    @DisplayName("저장되는 값은 원문이 아니라 해시다")
    void passwordIsStoredAsHash() {
        // given
        UUID userId = signUp();

        // when
        changePasswordHandler.handle(new ChangePasswordCommand(userId, PASSWORD, NEW_PASSWORD));

        // then
        PasswordHash stored = userStore.findUpdatedPassword(userId).orElseThrow();
        assertThat(stored.value()).isNotEqualTo(NEW_PASSWORD);
        assertThat(stored.value()).isEqualTo(passwordHasher.hash(NEW_PASSWORD));
        assertThat(userStore.findById(userId).orElseThrow().getPasswordHash()).isEqualTo(stored);
    }

    @Test
    @DisplayName("현재 비밀번호가 틀리면 갱신하지 않고 옛 비밀번호가 그대로 유효하다")
    void wrongCurrentPasswordIsRejected() {
        // given
        UUID userId = signUp();

        // when & then
        assertThatThrownBy(() -> changePasswordHandler.handle(
                new ChangePasswordCommand(userId, "WrongPassword1!", NEW_PASSWORD)))
                .isInstanceOf(InvalidCurrentPasswordException.class);
        assertThat(userStore.findUpdatedPassword(userId)).isEmpty();
        assertThatCode(() -> loginHandler.handle(new LoginCommand(EMAIL, PASSWORD)))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("소셜 전용 계정은 비밀번호를 만들 수 없다")
    void oauthOnlyUserIsRejected() {
        // given -> 빈 해시가 새 비밀번호로 덮이면 소셜 계정에 로컬 로그인이 생긴다
        UUID userId = oauthOnlyUser();

        // when & then
        assertThatThrownBy(() -> changePasswordHandler.handle(
                new ChangePasswordCommand(userId, PASSWORD, NEW_PASSWORD)))
                .isInstanceOf(PasswordNotSetException.class);
        assertThat(userStore.findUpdatedPassword(userId)).isEmpty();
        assertThat(userStore.findById(userId).orElseThrow().isPasswordNotSet()).isTrue();
    }

    @Test
    @DisplayName("존재하지 않는 유저는 막는다")
    void unknownUserIsRejected() {
        // given
        UUID unknownUserId = UuidCreator.getTimeOrderedEpoch();

        // when & then
        assertThatThrownBy(() -> changePasswordHandler.handle(
                new ChangePasswordCommand(unknownUserId, PASSWORD, NEW_PASSWORD)))
                .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    @DisplayName("비밀번호를 두 번 바꾸면 마지막 비밀번호만 유효하다")
    void changePasswordTwice() {
        // given
        UUID userId = signUp();
        String lastPassword = "LastPassword123!";
        changePasswordHandler.handle(new ChangePasswordCommand(userId, PASSWORD, NEW_PASSWORD));

        // when -> 두 번째는 첫 변경으로 만들어진 비밀번호를 현재 값으로 대야 한다
        changePasswordHandler.handle(new ChangePasswordCommand(userId, NEW_PASSWORD, lastPassword));

        // then
        assertThatCode(() -> loginHandler.handle(new LoginCommand(EMAIL, lastPassword)))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> loginHandler.handle(new LoginCommand(EMAIL, NEW_PASSWORD)))
                .isInstanceOf(InvalidCredentialsException.class);
    }
}
