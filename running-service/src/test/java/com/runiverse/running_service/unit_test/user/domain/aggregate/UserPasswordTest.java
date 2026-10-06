package com.runiverse.running_service.unit_test.user.domain.aggregate;

import com.runiverse.running_service.domain.user.User;
import com.runiverse.running_service.domain.user.exception.InvalidPasswordHashFormatException;
import com.runiverse.running_service.domain.user.exception.LastSignInMethodException;
import com.runiverse.running_service.domain.user.exception.PasswordHashRequiredException;
import com.runiverse.running_service.domain.user.exception.PasswordNotSetException;
import com.runiverse.running_service.domain.user.vo.Provider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class UserPasswordTest {

    private static final UUID USER_ID = UUID.fromString("0190a5b4-3c2d-7e1f-8a2b-123456789abc");
    private static final String EMAIL = "user@example.com";
    private static final String PASSWORD_HASH =
            "$argon2id$v=19$m=65536,t=3,p=1$"
                    + "c29tZXNhbHQ$"
                    + "c29tZWhhc2h2YWx1ZQ";
    private static final String NEW_PASSWORD_HASH =
            "$argon2id$v=19$m=65536,t=3,p=1$"
                    + "YW5vdGhlcnNhbHQ$"
                    + "YW5vdGhlcmhhc2h2YWx1ZQ";
    private static final String KAKAO_ID = "3812345678";

    @Nested
    @DisplayName("비밀번호 보유 여부 테스트")
    class IsPasswordNotSetTest {

        @Test
        @DisplayName("로컬 가입 유저는 비밀번호가 설정된 상태다")
        void localUserHasPassword() {
            // given
            User user = new User(USER_ID, EMAIL, PASSWORD_HASH);

            // then
            assertThat(user.isPasswordNotSet()).isFalse();
        }

        @Test
        @DisplayName("소셜 전용 유저는 비밀번호가 설정되지 않은 상태다")
        void oauthOnlyUserHasNoPassword() {
            // given -> 소셜 유저는 빈 해시를 갖는다
            User user = User.registerWithOauth(USER_ID, EMAIL, Provider.KAKAO, KAKAO_ID);

            // then
            assertThat(user.isPasswordNotSet()).isTrue();
        }
    }

    @Nested
    @DisplayName("비밀번호 변경 테스트")
    class ChangePasswordTest {

        @Test
        @DisplayName("로컬 가입 유저는 비밀번호를 변경할 수 있다")
        void changePasswordSuccess() {
            // given
            User user = new User(USER_ID, EMAIL, PASSWORD_HASH);

            // when
            user.changePassword(NEW_PASSWORD_HASH);

            // then
            assertThat(user.getPasswordHash().value()).isEqualTo(NEW_PASSWORD_HASH);
            assertThat(user.isPasswordNotSet()).isFalse();
        }

        @Test
        @DisplayName("소셜 전용 유저는 비밀번호를 변경할 수 없다")
        void changePasswordOnOauthOnlyUserFails() {
            // given
            User user = User.registerWithOauth(USER_ID, EMAIL, Provider.KAKAO, KAKAO_ID);

            // when & then -> 바꿀 비밀번호가 애초에 없다
            assertThatThrownBy(() -> user.changePassword(NEW_PASSWORD_HASH))
                    .isInstanceOf(PasswordNotSetException.class);
            assertThat(user.getPasswordHash().value()).isEmpty();
        }

        @Test
        @DisplayName("빈 해시로는 비밀번호를 변경할 수 없다")
        void changePasswordWithEmptyHashFails() {
            // given
            User user = new User(USER_ID, EMAIL, PASSWORD_HASH);

            // when & then -> 빈 해시가 들어가면 소셜 전용 계정으로 둔갑한다
            assertThatThrownBy(() -> user.changePassword(""))
                    .isInstanceOf(PasswordHashRequiredException.class);
            assertThat(user.getPasswordHash().value()).isEqualTo(PASSWORD_HASH);
        }

        @Test
        @DisplayName("null 해시로는 비밀번호를 변경할 수 없다")
        void changePasswordWithNullHashFails() {
            // given
            User user = new User(USER_ID, EMAIL, PASSWORD_HASH);

            // when & then
            assertThatThrownBy(() -> user.changePassword(null))
                    .isInstanceOf(PasswordHashRequiredException.class);
            assertThat(user.getPasswordHash().value()).isEqualTo(PASSWORD_HASH);
        }

        @Test
        @DisplayName("Argon2id 형식이 아닌 해시로는 비밀번호를 변경할 수 없다")
        void changePasswordWithInvalidHashFormatFails() {
            // given
            User user = new User(USER_ID, EMAIL, PASSWORD_HASH);

            // when & then -> 해싱하지 않은 원문이 그대로 넘어오는 실수를 막는다
            assertThatThrownBy(() -> user.changePassword("newPassword123!"))
                    .isInstanceOf(InvalidPasswordHashFormatException.class);
            assertThat(user.getPasswordHash().value()).isEqualTo(PASSWORD_HASH);
        }
    }

    @Nested
    @DisplayName("비밀번호 변경과 소셜 연결 해제 테스트")
    class ChangePasswordWithOauthTest {

        @Test
        @DisplayName("비밀번호가 있는 유저는 비밀번호를 바꿔도 소셜 연결을 해제할 수 있다")
        void unlinkOauthAfterChangePassword() {
            // given -> 로컬 가입 후 소셜을 연결한 유저
            User user = new User(USER_ID, EMAIL, PASSWORD_HASH);
            user.linkOauth(Provider.KAKAO, KAKAO_ID);

            // when
            user.changePassword(NEW_PASSWORD_HASH);
            user.unlinkOauth(Provider.KAKAO);

            // then
            assertThat(user.getOauthUser()).isEmpty();
        }

        @Test
        @DisplayName("소셜 전용 유저는 비밀번호 변경에 실패한 뒤에도 소셜 연결을 해제할 수 없다")
        void unlinkOauthStillBlockedForOauthOnlyUser() {
            // given
            User user = User.registerWithOauth(USER_ID, EMAIL, Provider.KAKAO, KAKAO_ID);

            // when & then -> 마지막 로그인 수단이 사라지면 계정에 접근할 수 없다
            assertThatThrownBy(() -> user.changePassword(NEW_PASSWORD_HASH))
                    .isInstanceOf(PasswordNotSetException.class);
            assertThatThrownBy(() -> user.unlinkOauth(Provider.KAKAO))
                    .isInstanceOf(LastSignInMethodException.class);
        }
    }
}
