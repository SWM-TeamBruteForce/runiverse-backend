package com.runiverse.running_service.unit_test.user.domain.aggregate;

import com.runiverse.running_service.domain.user.User;
import com.runiverse.running_service.domain.user.exception.IntroductionTooLongException;
import com.runiverse.running_service.domain.user.exception.InvalidEmailFormatException;
import com.runiverse.running_service.domain.user.exception.InvalidPasswordHashFormatException;
import com.runiverse.running_service.domain.common.exception.InvalidUserIdFormatException;
import com.runiverse.running_service.domain.user.exception.ProfileImageKeyRequiredException;
import com.runiverse.running_service.domain.user.exception.ProfileVisibilityRequiredException;
import com.runiverse.running_service.domain.user.vo.ProfileImageKey;
import com.runiverse.running_service.domain.user.vo.ProfileVisibility;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class UserAggregateTest {

    private static final UUID USER_ID = UUID.fromString("0190a5b4-3c2d-7e1f-8a2b-123456789abc");
    private static final String EMAIL = "user@example.com";
    private static final String PASSWORD_HASH =
            "$argon2id$v=19$m=65536,t=3,p=1$"
                    + "c29tZXNhbHQ$"
                    + "c29tZWhhc2h2YWx1ZQ";
    private static final String PROFILE_IMAGE_KEY = "profiles/0190a5b4-3c2d-7e1f-8a2b-123456789abc/photo.jpg";

    @Nested
    @DisplayName("전체 생성자 테스트")
    class FullConstructorTest {

        @Test
        @DisplayName("모든 정보를 사용해 사용자를 생성할 수 있다.")
        void createUserSuccess() {
            // given
            boolean alertConsent = true;
            String introduction = "함께 즐겁게 달려요!";

            // when
            User user = new User(
                    USER_ID,
                    EMAIL,
                    PASSWORD_HASH,
                    alertConsent,
                    PROFILE_IMAGE_KEY,
                    ProfileVisibility.FRIENDS,
                    introduction
            );

            // then
            assertThat(user.getUserId().value()).isEqualTo(USER_ID);
            assertThat(user.getEmail().value()).isEqualTo(EMAIL);
            assertThat(user.getPasswordHash().value()).isEqualTo(PASSWORD_HASH);
            assertThat(user.isAlertConsent()).isTrue();
            assertThat(user.getProfileImageKey())
                    .map(ProfileImageKey::value)
                    .contains(PROFILE_IMAGE_KEY);
            assertThat(user.getProfileVisibility()).isEqualTo(ProfileVisibility.FRIENDS);
            assertThat(user.getIntroduction().value()).isEqualTo(introduction);
        }

        @Nested
        @DisplayName("로컬 회원가입 생성자 테스트")
        class LocalSignupConstructorTest {

            @Test
            @DisplayName("로컬 회원가입 시 introduction은 빈 값, 공개 범위는 PUBLIC으로 생성된다")
            void createLocalUserSuccess() {
                // given
                boolean alertConsent = true;

                // when
                User user = new User(
                        USER_ID,
                        EMAIL,
                        PASSWORD_HASH,
                        alertConsent
                );

                // then
                assertThat(user.getUserId().value()).isEqualTo(USER_ID);
                assertThat(user.getEmail().value()).isEqualTo(EMAIL);
                assertThat(user.getPasswordHash().value()).isEqualTo(PASSWORD_HASH);
                assertThat(user.isAlertConsent()).isTrue();
                assertThat(user.getProfileVisibility()).isEqualTo(ProfileVisibility.PUBLIC);
                assertThat(user.getProfileImageKey()).isEmpty();   // 신규 가입자는 프로필 사진이 없다
                assertThat(user.getIntroduction().value()).isEmpty();
            }
        }

        @Nested
        @DisplayName("OAuth 회원가입 생성자 테스트")
        class OAuthSignupConstructorTest {

            @Test
            @DisplayName("OAuth 회원가입 시 비밀번호 해시와 소개는 빈 값이고 알림 동의는 true, 공개 범위는 PUBLIC이다")
            void createOAuthUserSuccess() {
                // when
                User user = new User(USER_ID, EMAIL);

                // then
                assertThat(user.getUserId().value()).isEqualTo(USER_ID);
                assertThat(user.getEmail().value()).isEqualTo(EMAIL);
                assertThat(user.getPasswordHash().value()).isEmpty();
                assertThat(user.isAlertConsent()).isTrue();   // 거래성 알림뿐이라 기본 수신
                assertThat(user.getProfileVisibility()).isEqualTo(ProfileVisibility.PUBLIC);
                assertThat(user.getProfileImageKey()).isEmpty();   // 신규 가입자는 프로필 사진이 없다
                assertThat(user.getIntroduction().value()).isEmpty();
            }
        }

        @Nested
        @DisplayName("기본 생성자 테스트")
        class DefaultConstructorTest {

            @Test
            @DisplayName("알림 동의와 소개가 없으면 기본값으로 생성된다")
            void createUserWithDefaultValuesSuccess() {
                // when
                User user = new User(
                        USER_ID,
                        EMAIL,
                        PASSWORD_HASH
                );

                // then
                assertThat(user.getUserId().value()).isEqualTo(USER_ID);
                assertThat(user.getEmail().value()).isEqualTo(EMAIL);
                assertThat(user.getPasswordHash().value()).isEqualTo(PASSWORD_HASH);
                assertThat(user.isAlertConsent()).isTrue();   // 거래성 알림뿐이라 기본 수신
                assertThat(user.getProfileVisibility()).isEqualTo(ProfileVisibility.PUBLIC);
                assertThat(user.getProfileImageKey()).isEmpty();   // 신규 가입자는 프로필 사진이 없다
                assertThat(user.getIntroduction().value()).isEmpty();
            }
        }

        @Nested
        @DisplayName("VO 검증 위임 테스트")
        class ValidationTest {

            @Test
            @DisplayName("UUIDv7이 아니면 사용자 생성에 실패한다")
            void createUserWithInvalidUserIdFails() {
                // given
                UUID uuidV4 = UUID.randomUUID();

                // when & then
                assertThatThrownBy(
                        () -> new User(
                                uuidV4,
                                EMAIL,
                                PASSWORD_HASH
                        )
                )
                        .isInstanceOf(InvalidUserIdFormatException.class)
                        .hasMessage("사용자 ID는 UUIDv7 형식이어야 합니다.");
            }

            @Test
            @DisplayName("이메일 형식이 올바르지 않으면 사용자 생성에 실패한다")
            void createUserWithInvalidEmailFails() {
                // given
                String invalidEmail = "invalid-email";

                // when & then
                assertThatThrownBy(
                        () -> new User(
                                USER_ID,
                                invalidEmail,
                                PASSWORD_HASH
                        )
                )
                        .isInstanceOf(InvalidEmailFormatException.class);
            }

            @Test
            @DisplayName("비밀번호 해시 형식이 올바르지 않으면 사용자 생성에 실패한다")
            void createUserWithInvalidPasswordHashFails() {
                // given
                String invalidPasswordHash = "plain-password";

                // when & then
                assertThatThrownBy(
                        () -> new User(
                                USER_ID,
                                EMAIL,
                                invalidPasswordHash
                        )
                )
                        .isInstanceOf(InvalidPasswordHashFormatException.class);
            }

            @Test
            @DisplayName("소개가 100자를 초과하면 사용자 생성에 실패한다")
            void createUserWithLongIntroductionFails() {
                // given
                String longIntroduction = "가".repeat(101);

                // when & then
                assertThatThrownBy(
                        () -> new User(
                                USER_ID,
                                EMAIL,
                                PASSWORD_HASH,
                                false,
                                null,
                                ProfileVisibility.PUBLIC,
                                longIntroduction
                        )
                )
                        .isInstanceOf(IntroductionTooLongException.class)
                        .hasMessage("소개는 100자를 초과할 수 없습니다.");
            }

            @Test
            @DisplayName("공개 범위가 null이면 사용자 생성에 실패한다")
            void createUserWithNullProfileVisibilityFails() {
                // when & then
                assertThatThrownBy(
                        () -> new User(
                                USER_ID,
                                EMAIL,
                                PASSWORD_HASH,
                                false,
                                null,
                                null,
                                ""
                        )
                )
                        .isInstanceOf(ProfileVisibilityRequiredException.class)
                        .hasMessage("프로필 공개 범위는 필수입니다.");
            }

            @Test
            @DisplayName("프로필 이미지 키가 비어 있으면 사용자 생성에 실패한다")
            void createUserWithBlankProfileImageKeyFails() {
                // given -> 사진 없음은 null로 표현하고, 빈 문자열은 잘못된 입력으로 본다
                String blankKey = "   ";

                // when & then
                assertThatThrownBy(
                        () -> new User(
                                USER_ID,
                                EMAIL,
                                PASSWORD_HASH,
                                false,
                                blankKey,
                                ProfileVisibility.PUBLIC,
                                ""
                        )
                )
                        .isInstanceOf(ProfileImageKeyRequiredException.class);
            }
        }
    }
}
