package com.runiverse.running_service.unit_test.user.domain.aggregate;

import com.runiverse.running_service.domain.user.User;
import com.runiverse.running_service.domain.common.exception.InvalidUserIdFormatException;
import com.runiverse.running_service.domain.user.exception.LastSignInMethodException;
import com.runiverse.running_service.domain.user.exception.OauthAlreadyLinkedException;
import com.runiverse.running_service.domain.user.exception.OauthNotLinkedException;
import com.runiverse.running_service.domain.user.vo.Provider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class UserOauthTest {

    private static final UUID USER_ID = UUID.fromString("0190a5b4-3c2d-7e1f-8a2b-123456789abc");
    private static final String EMAIL = "user@example.com";
    private static final String PASSWORD_HASH =
            "$argon2id$v=19$m=65536,t=3,p=1$"
                    + "c29tZXNhbHQ$"
                    + "c29tZWhhc2h2YWx1ZQ";
    private static final String KAKAO_ID = "3812345678";
    private static final String GOOGLE_ID = "109876543210987654321";

    @Nested
    @DisplayName("소셜 회원가입 테스트")
    class RegisterWithOauthTest {

        @Test
        @DisplayName("소셜 회원가입 시 유저 생성과 연결이 함께 이루어진다")
        void registerWithOauthSuccess() {
            // when
            User user = User.registerWithOauth(USER_ID, EMAIL, Provider.KAKAO, KAKAO_ID);

            // then -> 연결 없는 소셜 유저가 만들어지면 로그인할 방법이 없다
            assertThat(user.getUserId().value()).isEqualTo(USER_ID);
            assertThat(user.getEmail().value()).isEqualTo(EMAIL);
            assertThat(user.getPasswordHash().value()).isEmpty();
            assertThat(user.getOauthUser()).isPresent();
            assertThat(user.hasProvider(Provider.KAKAO)).isTrue();
        }

        @Test
        @DisplayName("UUIDv7이 아니면 회원가입에 실패한다")
        void registerWithInvalidUserIdFails() {
            // given
            UUID uuidV4 = UUID.randomUUID();

            // when & then
            assertThatThrownBy(() -> User.registerWithOauth(uuidV4, EMAIL, Provider.KAKAO, KAKAO_ID))
                    .isInstanceOf(InvalidUserIdFormatException.class);
        }
    }

    @Nested
    @DisplayName("소셜 연결 테스트")
    class LinkOauthTest {

        @Test
        @DisplayName("로컬 가입 유저에 소셜 계정을 연결할 수 있다")
        void linkOauthToLocalUserSuccess() {
            // given
            User user = new User(USER_ID, EMAIL, PASSWORD_HASH);

            // when
            user.linkOauth(Provider.KAKAO, KAKAO_ID);

            // then
            assertThat(user.getOauthUser()).isPresent();
            assertThat(user.hasProvider(Provider.KAKAO)).isTrue();
        }

        @Test
        @DisplayName("이미 연결된 유저는 다른 provider를 연결할 수 없다")
        void linkAnotherProviderFails() {
            // given -> 한 유저는 소셜 계정을 하나만 가진다
            User user = User.registerWithOauth(USER_ID, EMAIL, Provider.KAKAO, KAKAO_ID);

            // when & then
            assertThatThrownBy(() -> user.linkOauth(Provider.GOOGLE, GOOGLE_ID))
                    .isInstanceOf(OauthAlreadyLinkedException.class)
                    .hasMessage("이미 연결된 소셜 계정입니다.");

            assertThat(user.hasProvider(Provider.KAKAO)).isTrue();
            assertThat(user.hasProvider(Provider.GOOGLE)).isFalse();
        }

        @Test
        @DisplayName("이미 연결된 provider를 다시 연결하면 예외가 발생한다")
        void linkDuplicateProviderFails() {
            // given -> 한 유저는 같은 provider를 두 번 연결할 수 없다
            User user = User.registerWithOauth(USER_ID, EMAIL, Provider.KAKAO, KAKAO_ID);

            // when & then
            assertThatThrownBy(() -> user.linkOauth(Provider.KAKAO, "9999999999"))
                    .isInstanceOf(OauthAlreadyLinkedException.class)
                    .hasMessage("이미 연결된 소셜 계정입니다.");

            assertThat(user.getOauthUser()).isPresent();
        }
    }

    @Nested
    @DisplayName("소셜 연결 해제 테스트")
    class UnlinkOauthTest {

        @Test
        @DisplayName("비밀번호가 있으면 마지막 소셜 연결도 해제할 수 있다")
        void unlinkLastOauthWithPasswordSuccess() {
            // given
            User user = new User(USER_ID, EMAIL, PASSWORD_HASH);
            user.linkOauth(Provider.KAKAO, KAKAO_ID);

            // when
            user.unlinkOauth(Provider.KAKAO);

            // then -> 비밀번호로 로그인할 수 있으니 계정이 잠기지 않는다
            assertThat(user.getOauthUser()).isEmpty();
            assertThat(user.hasProvider(Provider.KAKAO)).isFalse();
        }

        @Test
        @DisplayName("소셜 전용 유저의 마지막 연결은 해제할 수 없다")
        void unlinkLastSignInMethodFails() {
            // given -> 해제하면 아무 방법으로도 로그인할 수 없게 된다
            User user = User.registerWithOauth(USER_ID, EMAIL, Provider.KAKAO, KAKAO_ID);

            // when & then
            assertThatThrownBy(() -> user.unlinkOauth(Provider.KAKAO))
                    .isInstanceOf(LastSignInMethodException.class)
                    .hasMessage("마지막 로그인 수단은 해제할 수 없습니다.");

            assertThat(user.getOauthUser()).isPresent();
        }

        @Test
        @DisplayName("연결된 적 없는 provider를 해제하면 예외가 발생한다")
        void unlinkNotLinkedProviderFails() {
            // given
            User user = User.registerWithOauth(USER_ID, EMAIL, Provider.KAKAO, KAKAO_ID);

            // when & then -> 존재 확인이 먼저라 '마지막 수단' 예외가 아니어야 한다
            assertThatThrownBy(() -> user.unlinkOauth(Provider.GOOGLE))
                    .isInstanceOf(OauthNotLinkedException.class)
                    .hasMessage("연결되지 않은 소셜 계정입니다.");
        }
    }

    @Nested
    @DisplayName("연결 조회 테스트")
    class OauthUserQueryTest {

        @Test
        @DisplayName("연결이 없는 유저의 조회 결과는 비어 있다")
        void localUserHasNoOauthUser() {
            // given
            User user = new User(USER_ID, EMAIL, PASSWORD_HASH);

            // when & then
            assertThat(user.getOauthUser()).isEmpty();
            assertThat(user.hasProvider(Provider.KAKAO)).isFalse();
        }
    }
}
