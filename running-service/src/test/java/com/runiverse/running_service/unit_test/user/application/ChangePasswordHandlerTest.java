package com.runiverse.running_service.unit_test.user.application;

import com.github.f4b6a3.uuid.UuidCreator;
import com.runiverse.running_service.application.common.port.out.PasswordHashPort;
import com.runiverse.running_service.application.user.command.password.ChangePasswordCommand;
import com.runiverse.running_service.application.user.command.password.ChangePasswordHandler;
import com.runiverse.running_service.application.user.exception.InvalidCurrentPasswordException;
import com.runiverse.running_service.application.user.exception.PasswordNotSetException;
import com.runiverse.running_service.application.user.exception.UserNotFoundException;
import com.runiverse.running_service.application.user.port.out.LoadUserByIdPort;
import com.runiverse.running_service.application.user.port.out.UpdatePasswordPort;
import com.runiverse.running_service.domain.user.User;
import com.runiverse.running_service.domain.user.exception.InvalidPasswordHashFormatException;
import com.runiverse.running_service.domain.common.exception.InvalidUserIdFormatException;
import com.runiverse.running_service.domain.user.vo.PasswordHash;
import com.runiverse.running_service.domain.user.vo.Provider;
import com.runiverse.running_service.domain.common.vo.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@DisplayName("비밀번호 변경 단위 테스트")
public class ChangePasswordHandlerTest {

    private static final String EMAIL = "runner@runiverse.com";
    private static final String CURRENT_PASSWORD = "Password123!";
    private static final String NEW_PASSWORD = "NewPassword123!";
    private static final String CURRENT_HASH =
            "$argon2id$v=19$m=16384,t=2,p=1$c29tZXNhbHQ$Y3VycmVudGhhc2g";
    private static final String NEW_HASH =
            "$argon2id$v=19$m=16384,t=2,p=1$YW5vdGhlcnNhbHQ$bmV3aGFzaA";
    private static final String KAKAO_ID = "3812345678";

    @Mock
    private LoadUserByIdPort loadUserByIdPort;

    @Mock
    private PasswordHashPort passwordHashPort;

    @Mock
    private UpdatePasswordPort updatePasswordPort;

    @InjectMocks
    private ChangePasswordHandler handler;

    @Test
    @DisplayName("현재 비밀번호가 맞으면 새 해시로 갱신한다")
    void changePasswordSuccess() {
        // given
        UUID userId = UuidCreator.getTimeOrderedEpoch();
        given(loadUserByIdPort.loadById(new UserId(userId)))
                .willReturn(Optional.of(new User(userId, EMAIL, CURRENT_HASH)));
        given(passwordHashPort.matches(CURRENT_PASSWORD, CURRENT_HASH)).willReturn(true);
        given(passwordHashPort.hash(NEW_PASSWORD)).willReturn(NEW_HASH);

        // when
        handler.handle(new ChangePasswordCommand(userId, CURRENT_PASSWORD, NEW_PASSWORD));

        // then -> 저장되는 값은 원문이 아니라 해시여야 한다
        verify(updatePasswordPort).updatePassword(new UserId(userId), new PasswordHash(NEW_HASH));
    }

    @Test
    @DisplayName("현재 비밀번호가 틀리면 갱신하지 않고 막는다")
    void wrongCurrentPasswordIsRejected() {
        // given
        UUID userId = UuidCreator.getTimeOrderedEpoch();
        given(loadUserByIdPort.loadById(new UserId(userId)))
                .willReturn(Optional.of(new User(userId, EMAIL, CURRENT_HASH)));
        given(passwordHashPort.matches("WrongPassword1!", CURRENT_HASH)).willReturn(false);

        // when & then -> 토큰만으로는 본인 확인이 되지 않는다
        assertThatThrownBy(() -> handler.handle(
                new ChangePasswordCommand(userId, "WrongPassword1!", NEW_PASSWORD)))
                .isInstanceOf(InvalidCurrentPasswordException.class);
        verifyNoInteractions(updatePasswordPort);
    }

    @Test
    @DisplayName("소셜 전용 계정은 현재 비밀번호를 대조하기 전에 막는다")
    void oauthOnlyUserIsRejected() {
        // given -> 소셜 유저는 해시가 비어 있어 어떤 입력과도 대조할 수 없다
        UUID userId = UuidCreator.getTimeOrderedEpoch();
        given(loadUserByIdPort.loadById(new UserId(userId)))
                .willReturn(Optional.of(User.registerWithOauth(userId, EMAIL, Provider.KAKAO, KAKAO_ID)));

        // when & then
        assertThatThrownBy(() -> handler.handle(
                new ChangePasswordCommand(userId, CURRENT_PASSWORD, NEW_PASSWORD)))
                .isInstanceOf(PasswordNotSetException.class);
        verifyNoInteractions(passwordHashPort, updatePasswordPort);
    }

    @Test
    @DisplayName("존재하지 않는 유저면 해싱도 갱신도 하지 않는다")
    void unknownUserIsRejected() {
        // given
        UUID userId = UuidCreator.getTimeOrderedEpoch();
        given(loadUserByIdPort.loadById(new UserId(userId))).willReturn(Optional.empty());

        // when & then -> 계정 존재 여부는 응답에서 500으로 마스킹된다
        assertThatThrownBy(() -> handler.handle(
                new ChangePasswordCommand(userId, CURRENT_PASSWORD, NEW_PASSWORD)))
                .isInstanceOf(UserNotFoundException.class);
        verifyNoInteractions(passwordHashPort, updatePasswordPort);
    }

    @Test
    @DisplayName("해싱되지 않은 값이 저장될 뻔하면 도메인이 막는다")
    void rawPasswordIsNeverStored() {
        // given -> 포트가 해싱을 건너뛰고 원문을 돌려주는 상황을 가정한다
        UUID userId = UuidCreator.getTimeOrderedEpoch();
        given(loadUserByIdPort.loadById(new UserId(userId)))
                .willReturn(Optional.of(new User(userId, EMAIL, CURRENT_HASH)));
        given(passwordHashPort.matches(CURRENT_PASSWORD, CURRENT_HASH)).willReturn(true);
        given(passwordHashPort.hash(NEW_PASSWORD)).willReturn(NEW_PASSWORD);

        // when & then
        assertThatThrownBy(() -> handler.handle(
                new ChangePasswordCommand(userId, CURRENT_PASSWORD, NEW_PASSWORD)))
                .isInstanceOf(InvalidPasswordHashFormatException.class);
        verifyNoInteractions(updatePasswordPort);
    }

    @Test
    @DisplayName("userId 형식이 어긋나면 조회 전에 VO가 막는다")
    void invalidUserIdIsRejectedBeforeAnyPortCall() {
        // given -> UUIDv7이 아닌 값이다
        UUID uuidV4 = UUID.fromString("123e4567-e89b-42d3-a456-426614174000");

        // when & then
        assertThatThrownBy(() -> handler.handle(
                new ChangePasswordCommand(uuidV4, CURRENT_PASSWORD, NEW_PASSWORD)))
                .isInstanceOf(InvalidUserIdFormatException.class);
        verifyNoInteractions(loadUserByIdPort, passwordHashPort, updatePasswordPort);
    }
}
