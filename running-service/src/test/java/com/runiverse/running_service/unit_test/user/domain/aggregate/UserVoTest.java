package com.runiverse.running_service.unit_test.user.domain.aggregate;


import com.runiverse.running_service.domain.user.exception.EmailRequiredException;
import com.runiverse.running_service.domain.user.exception.EmailTooLongException;
import com.runiverse.running_service.domain.user.exception.IntroductionRequiredException;
import com.runiverse.running_service.domain.user.exception.IntroductionTooLongException;
import com.runiverse.running_service.domain.user.exception.InvalidEmailFormatException;
import com.runiverse.running_service.domain.user.exception.InvalidPasswordHashFormatException;
import com.runiverse.running_service.domain.common.exception.InvalidUserIdFormatException;
import com.runiverse.running_service.domain.user.exception.PasswordHashRequiredException;
import com.runiverse.running_service.domain.user.exception.ProfileImageKeyRequiredException;
import com.runiverse.running_service.domain.user.exception.ProfileImageKeyTooLongException;
import com.runiverse.running_service.domain.user.exception.ProfileVisibilityRequiredException;
import com.runiverse.running_service.domain.user.exception.UnsupportedProfileVisibilityException;
import com.runiverse.running_service.domain.common.exception.UserIdRequiredException;
import com.runiverse.running_service.domain.user.vo.Email;
import com.runiverse.running_service.domain.user.vo.Introduction;
import com.runiverse.running_service.domain.user.vo.PasswordHash;
import com.runiverse.running_service.domain.user.vo.ProfileImageKey;
import com.runiverse.running_service.domain.user.vo.ProfileVisibility;
import com.runiverse.running_service.domain.common.vo.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class UserVoTest {

    // UserId 테스트
    @Nested
    @DisplayName("userId 테스트")
    class UserIdTest {

        @Test
        @DisplayName("UUIDv7으로 사용자 ID를 생성할 수 있다.")
        void createUserIdWithUuidV7Success() {
            // given
            UUID uuidV7 = UUID.fromString("0190a5b4-3c2d-7e1f-8a2b-123456789abc");

            // when
            UserId userId = new UserId(uuidV7);

            // then -> uuid7로 만든게 맞는지 확인
            assertThat(userId.value()).isEqualTo(uuidV7);
            assertThat(userId.value().version()).isEqualTo(7);
        }

        @Test
        @DisplayName("uuid v7이 아니면 예외가 발생한다")
        void createUserIdWithUuidV4Fails() {
            // given
            UUID uuidV4 = UUID.randomUUID();

            // when & then
            assertThatThrownBy(() -> new UserId(uuidV4))
                    .isInstanceOf(InvalidUserIdFormatException.class)
                    .hasMessage("사용자 ID는 UUIDv7 형식이어야 합니다.");
        }

        @Test
        @DisplayName("UserId가 null이면 예외가 발생한다")
        void createUserIdWithNullFails() {
            assertThatThrownBy(() -> new UserId(null))
                    .isInstanceOf(UserIdRequiredException.class)
                    .hasMessage("사용자 ID는 필수입니다.");
        }

        @Test
        @DisplayName("같은 UUID를 가진 UserId는 같은 값 객체이다")
        void equalsTest() {
            // given
            UUID uuid = UUID.fromString("0190a5b4-3c2d-7e1f-8a2b-123456789abc");

            UserId first = new UserId(uuid);
            UserId second = new UserId(uuid);

            // then
            assertThat(first)
                    .isEqualTo(second)
                    .hasSameHashCodeAs(second);

            assertThat(first)
                    .isNotSameAs(second);
        }
    }

    // Email 테스트
    @Nested
    @DisplayName("Email 테스트")
    class EmailTest {

        @Test
        @DisplayName("올바른 이메일은 생성할 수 있다.")
        void createEmailSuccess() {
            // given
            String value = "user@example.com";

            // when
            Email email = new Email(value);

            // then
            assertThat(email.value()).isEqualTo(value);
        }

        @Test
        @DisplayName("잘못된 이메일 형식이면 예외가 발생한다.")
        void createInvalidEmailFails() {
            // given
            String value = "invalid-email";

            assertThatThrownBy(() -> new Email(value))
                    .isInstanceOf(InvalidEmailFormatException.class);
        }

        @Test
        @DisplayName("골뱅이 뒤의 도메인이 없으면 예외가 발생한다")
        void createEmailWithoutDomainFails() {
            // given
            String value = "user@";

            // when & then
            assertThatThrownBy(() -> new Email(value))
                    .isInstanceOf(InvalidEmailFormatException.class);
        }

        @Test
        @DisplayName("골뱅이 앞의 사용자 이름이 없으면 예외가 발생한다")
        void createEmailWithoutLocalPartFails() {
            // given
            String value = "@example.com";

            // when & then
            assertThatThrownBy(() -> new Email(value))
                    .isInstanceOf(InvalidEmailFormatException.class);
        }

        @Test
        @DisplayName("이메일이 빈 문자열이면 예외가 발생한다")
        void createEmptyEmailFails() {
            // given
            String value = "";

            // when & then
            assertThatThrownBy(() -> new Email(value))
                    .isInstanceOf(EmailRequiredException.class);
        }

        @Test
        @DisplayName("이메일이 null이면 예외가 발생한다")
        void createEmailWithNullFails() {
            // when & then
            assertThatThrownBy(() -> new Email(null))
                    .isInstanceOf(EmailRequiredException.class);
        }

        @Test
        @DisplayName("이메일이 최대 길이를 초과하면 예외가 발생한다")
        void createEmailOverMaxLengthFails() {
            // given
            String value = "a".repeat(245) + "@example.com";

            // when & then
            assertThatThrownBy(() -> new Email(value))
                    .isInstanceOf(EmailTooLongException.class);
        }

        @Test
        @DisplayName("같은 문자열을 가진 Email은 같은 값 객체이다")
        void emailEqualsTest() {
            // given
            Email first = new Email("user@example.com");
            Email second = new Email("user@example.com");

            // then
            assertThat(first)
                    .isEqualTo(second)
                    .hasSameHashCodeAs(second);

            assertThat(first).isNotSameAs(second);
        }

        @Test
        @DisplayName("대문자가 섞여 있어도 소문자로 정규화된다")
        void createEmailNormalizesToLowerCase() {
            // given & when
            Email email = new Email("USER@Example.COM");

            // then - 정규화하지 않으면 같은 사람이 대소문자만 바꿔 중복 가입할 수 있다
            assertThat(email.value()).isEqualTo("user@example.com");
        }

        @Test
        @DisplayName("앞뒤 공백은 제거된다")
        void createEmailTrimsWhitespace() {
            // given & when
            Email email = new Email("  user@example.com  ");

            // then
            assertThat(email.value()).isEqualTo("user@example.com");
        }

        @Test
        @DisplayName("공백만 있는 문자열은 EmailRequiredException이 발생한다")
        void createBlankEmailFails() {
            // trim 뒤에 비는 값이라 null과 같게 취급해야 한다
            assertThatThrownBy(() -> new Email("   "))
                    .isInstanceOf(EmailRequiredException.class);
        }

        @Test
        @DisplayName("대소문자와 공백만 다른 이메일은 같은 값 객체이다")
        void emailsDifferingOnlyByCaseAreEqual() {
            // given & when
            Email first = new Email("User@Example.com");
            Email second = new Email(" user@example.com ");

            // then - 이메일 인증 전체가 이 전제 위에 서 있다
            assertThat(first)
                    .isEqualTo(second)
                    .hasSameHashCodeAs(second);
        }
    }

    // PasswordHash 테스트
    @Nested
    @DisplayName("PasswordHash 테스트")
    class PasswordHashTest {

        private static final String VALID_ARGON2ID_HASH =
                "$argon2id$v=19$m=65536,t=3,p=1$" +
                        "c29tZXNhbHQ$" +
                        "c29tZWhhc2h2YWx1ZQ";

        @Test
        @DisplayName("올바른 Argon2id 해시는 생성할 수 있다")
        void createPasswordHashSuccess() {
            // given
            String value = VALID_ARGON2ID_HASH;

            // when
            PasswordHash passwordHash = new PasswordHash(value);

            // then
            assertThat(passwordHash.value()).isEqualTo(value);
        }

        @Test
        @DisplayName("OAuth 사용자는 빈 비밀번호 해시를 가질 수 있다")
        void createEmptyPasswordHashSuccess() {
            // given
            String value = "";

            // when
            PasswordHash passwordHash = new PasswordHash(value);

            // then
            assertThat(passwordHash.value()).isEmpty();
        }

        @Test
        @DisplayName("평문 비밀번호는 비밀번호 해시로 사용할 수 없다")
        void createPasswordHashWithPlainTextFails() {
            // given
            String value = "password1234";

            // when & then
            assertThatThrownBy(() -> new PasswordHash(value))
                    .isInstanceOf(InvalidPasswordHashFormatException.class)
                    .hasMessage(
                            "비밀번호 해시는 빈 값이거나 올바른 Argon2id 형식이어야 합니다."
                    );
        }

        @Test
        @DisplayName("잘못된 Argon2id 해시 형식이면 예외가 발생한다")
        void createInvalidArgon2idHashFails() {
            // given
            String value = "$argon2id$invalid-hash";

            // when & then
            assertThatThrownBy(() -> new PasswordHash(value))
                    .isInstanceOf(InvalidPasswordHashFormatException.class)
                    .hasMessage(
                            "비밀번호 해시는 빈 값이거나 올바른 Argon2id 형식이어야 합니다."
                    );
        }

        @Test
        @DisplayName("비밀번호 해시가 null이면 예외가 발생한다")
        void createPasswordHashWithNullFails() {
            // when & then
            assertThatThrownBy(() -> new PasswordHash(null))
                    .isInstanceOf(PasswordHashRequiredException.class)
                    .hasMessage("비밀번호 해시는 필수입니다.");
        }

        @Test
        @DisplayName("같은 해시를 가진 PasswordHash는 같은 값 객체이다")
        void passwordHashEqualsTest() {
            // given
            PasswordHash first = new PasswordHash(VALID_ARGON2ID_HASH);
            PasswordHash second = new PasswordHash(VALID_ARGON2ID_HASH);

            // then
            assertThat(first)
                    .isEqualTo(second)
                    .hasSameHashCodeAs(second);

            assertThat(first).isNotSameAs(second);
        }
    }

    // Introduction 테스트
    @Nested
    @DisplayName("Introduction 테스트")
    class IntroductionTest {

        @Test
        @DisplayName("100자 이하의 소개는 생성할 수 있다")
        void createIntroductionSuccess() {
            // given
            String value = "함께 즐겁게 달려요!";

            // when
            Introduction introduction = new Introduction(value);

            // then
            assertThat(introduction.value()).isEqualTo(value);
        }

        @Test
        @DisplayName("빈 소개는 생성할 수 있다")
        void createEmptyIntroductionSuccess() {
            // given
            String value = "";

            // when
            Introduction introduction = new Introduction(value);

            // then
            assertThat(introduction.value()).isEmpty();
        }

        @Test
        @DisplayName("소개가 정확히 100자이면 생성할 수 있다")
        void createIntroductionWithMaxLengthSuccess() {
            // given
            String value = "가".repeat(100);

            // when
            Introduction introduction = new Introduction(value);

            // then
            assertThat(introduction.value())
                    .hasSize(100)
                    .isEqualTo(value);
        }

        @Test
        @DisplayName("소개가 100자를 초과하면 예외가 발생한다")
        void createIntroductionOverMaxLengthFails() {
            // given
            String value = "가".repeat(101);

            // when & then
            assertThatThrownBy(() -> new Introduction(value))
                    .isInstanceOf(IntroductionTooLongException.class)
                    .hasMessage("소개는 100자를 초과할 수 없습니다.");
        }

        @Test
        @DisplayName("소개가 null이면 예외가 발생한다")
        void createIntroductionWithNullFails() {
            // when & then
            assertThatThrownBy(() -> new Introduction(null))
                    .isInstanceOf(IntroductionRequiredException.class)
                    .hasMessage("소개는 null일 수 없습니다.");
        }

        @Test
        @DisplayName("같은 소개를 가진 Introduction은 같은 값 객체이다")
        void introductionEqualsTest() {
            // given
            Introduction first = new Introduction("함께 달려요!");
            Introduction second = new Introduction("함께 달려요!");

            // then
            assertThat(first)
                    .isEqualTo(second)
                    .hasSameHashCodeAs(second);

            assertThat(first).isNotSameAs(second);
        }
    }

    // ProfileImageKey 테스트
    @Nested
    @DisplayName("ProfileImageKey 테스트")
    class ProfileImageKeyTest {

        private static final String KEY = "profiles/0190a5b4-3c2d-7e1f-8a2b-123456789abc/photo.jpg";

        @Test
        @DisplayName("255자 이하의 키는 생성할 수 있다")
        void createProfileImageKeySuccess() {
            // when
            ProfileImageKey key = new ProfileImageKey(KEY);

            // then
            assertThat(key.value()).isEqualTo(KEY);
        }

        @Test
        @DisplayName("앞뒤 공백은 제거된다")
        void createProfileImageKeyTrimsWhitespace() {
            // when
            ProfileImageKey key = new ProfileImageKey("  " + KEY + "  ");

            // then
            assertThat(key.value()).isEqualTo(KEY);
        }

        @Test
        @DisplayName("키가 정확히 255자이면 생성할 수 있다")
        void createProfileImageKeyWithMaxLengthSuccess() {
            // given
            String value = "a".repeat(255);

            // when
            ProfileImageKey key = new ProfileImageKey(value);

            // then
            assertThat(key.value()).hasSize(255);
        }

        @Test
        @DisplayName("키가 255자를 초과하면 예외가 발생한다")
        void createProfileImageKeyOverMaxLengthFails() {
            // given
            String value = "a".repeat(256);

            // when & then
            assertThatThrownBy(() -> new ProfileImageKey(value))
                    .isInstanceOf(ProfileImageKeyTooLongException.class);
        }

        @Test
        @DisplayName("키가 null이면 예외가 발생한다")
        void createProfileImageKeyWithNullFails() {
            // when & then -> 사진 없음은 VO가 아니라 null 필드로 표현한다
            assertThatThrownBy(() -> new ProfileImageKey(null))
                    .isInstanceOf(ProfileImageKeyRequiredException.class);
        }

        @Test
        @DisplayName("키가 빈 문자열이거나 공백뿐이면 예외가 발생한다")
        void createProfileImageKeyWithBlankFails() {
            // when & then
            assertThatThrownBy(() -> new ProfileImageKey(""))
                    .isInstanceOf(ProfileImageKeyRequiredException.class);

            assertThatThrownBy(() -> new ProfileImageKey("   "))
                    .isInstanceOf(ProfileImageKeyRequiredException.class);
        }

        @Test
        @DisplayName("같은 키를 가진 ProfileImageKey는 같은 값 객체이다")
        void profileImageKeyEqualsTest() {
            // given
            ProfileImageKey first = new ProfileImageKey(KEY);
            ProfileImageKey second = new ProfileImageKey(KEY);

            // then
            assertThat(first)
                    .isEqualTo(second)
                    .hasSameHashCodeAs(second);

            assertThat(first).isNotSameAs(second);
        }
    }

    @Nested
    @DisplayName("프로필 공개 범위 테스트")
    class ProfileVisibilityTest {

        @Test
        @DisplayName("대소문자와 공백에 관계없이 공개 범위를 만들 수 있다")
        void profileVisibilityFromSuccess() {
            // when & then
            assertThat(ProfileVisibility.from("PUBLIC")).isEqualTo(ProfileVisibility.PUBLIC);
            assertThat(ProfileVisibility.from("friends")).isEqualTo(ProfileVisibility.FRIENDS);
            assertThat(ProfileVisibility.from("  Public  ")).isEqualTo(ProfileVisibility.PUBLIC);
        }

        @Test
        @DisplayName("enum이라 from()은 새 객체를 만들지 않는다")
        void profileVisibilityFromReturnsSameInstance() {
            // when & then
            assertThat(ProfileVisibility.from("public")).isSameAs(ProfileVisibility.PUBLIC);
        }

        @Test
        @DisplayName("null이거나 공백뿐이면 예외가 발생한다")
        void profileVisibilityRequiredFails() {
            // when & then
            assertThatThrownBy(() -> ProfileVisibility.from(null))
                    .isInstanceOf(ProfileVisibilityRequiredException.class)
                    .hasMessage("프로필 공개 범위는 필수입니다.");

            assertThatThrownBy(() -> ProfileVisibility.from("   "))
                    .isInstanceOf(ProfileVisibilityRequiredException.class);
        }

        @Test
        @DisplayName("지원하지 않는 값이면 예외가 발생한다")
        void profileVisibilityUnsupportedFails() {
            // when & then -> valueOf()의 예외가 그대로 새어나가면 500이 된다
            assertThatThrownBy(() -> ProfileVisibility.from("PRIVATE"))
                    .isInstanceOf(UnsupportedProfileVisibilityException.class)
                    .hasMessage("지원하지 않는 프로필 공개 범위입니다.");
        }
    }
}
