package com.runiverse.running_service.unit_test.user.application;

import com.runiverse.running_service.application.user.common.ImageContentType;
import com.runiverse.running_service.application.user.common.UserImageType;
import com.runiverse.running_service.application.user.exception.BackgroundImageNotUploadedException;
import com.runiverse.running_service.application.user.exception.InvalidBackgroundImageException;
import com.runiverse.running_service.application.user.exception.InvalidProfileImageException;
import com.runiverse.running_service.application.user.exception.ProfileImageNotUploadedException;
import com.runiverse.running_service.domain.user.User;
import com.runiverse.running_service.domain.user.vo.ProfileVisibility;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("사용자 이미지 종류 단위 테스트")
public class UserImageTypeTest {

    private static final UUID OWNER = UUID.fromString("019ff918-a5ac-75b6-b20b-350454888411");
    private static final UUID IMAGE_ID = UUID.fromString("019ffa54-917f-7477-9482-5792597ef3b0");
    private static final UUID OTHER = UUID.fromString("00000000-0000-0000-0000-000000000009");
    private static final String PASSWORD_HASH =
            "$argon2id$v=19$m=16384,t=2,p=1$c29tZXNhbHQ$aGFzaHZhbHVl";

    @Test
    @DisplayName("key는 종류별 prefix와 소유자 아래에 mimeType으로 정한 확장자로 만든다")
    void createKeyBuildsKeyUnderTypePrefix() {
        // when & then
        assertThat(UserImageType.PROFILE.createKey(OWNER, IMAGE_ID, ImageContentType.PNG))
                .isEqualTo("profiles/%s/%s.png".formatted(OWNER, IMAGE_ID));
        assertThat(UserImageType.BACKGROUND.createKey(OWNER, IMAGE_ID, ImageContentType.WEBP))
                .isEqualTo("backgrounds/%s/%s.webp".formatted(OWNER, IMAGE_ID));
    }

    @Test
    @DisplayName("prefix는 {종류}/{userId}/ 이다")
    void prefixOfEndsWithSeparator() {
        // when & then -> 탈퇴 시 이 prefix 전체를 지우므로 유저 폴더 경계까지 포함해야 한다
        assertThat(UserImageType.PROFILE.prefixOf(OWNER)).isEqualTo("profiles/%s/".formatted(OWNER));
        assertThat(UserImageType.BACKGROUND.prefixOf(OWNER)).isEqualTo("backgrounds/%s/".formatted(OWNER));
    }

    @ParameterizedTest
    @EnumSource(UserImageType.class)
    @DisplayName("자기 prefix로 만든 key는 소유자로 인정한다")
    void ownKeyIsOwned(UserImageType type) {
        // given
        String key = type.createKey(OWNER, IMAGE_ID, ImageContentType.JPEG);

        // when & then
        assertThat(type.isOwnedBy(key, OWNER)).isTrue();
    }

    @ParameterizedTest
    @EnumSource(UserImageType.class)
    @DisplayName("다른 사용자의 key는 소유자로 인정하지 않는다")
    void otherUsersKeyIsNotOwned(UserImageType type) {
        // given
        String key = type.createKey(OWNER, IMAGE_ID, ImageContentType.JPEG);

        // when & then -> 남의 key를 자기 사진으로 붙이는 것을 막는 지점이다
        assertThat(type.isOwnedBy(key, OTHER)).isFalse();
    }

    @Test
    @DisplayName("같은 유저라도 다른 종류의 key는 소유자로 인정하지 않는다")
    void otherTypesKeyIsNotOwned() {
        // given
        String profileKey = UserImageType.PROFILE.createKey(OWNER, IMAGE_ID, ImageContentType.JPEG);
        String backgroundKey = UserImageType.BACKGROUND.createKey(OWNER, IMAGE_ID, ImageContentType.JPEG);

        // when & then -> 프로필 사진을 배경으로, 배경을 프로필로 돌려 쓸 수 없다
        assertThat(UserImageType.BACKGROUND.isOwnedBy(profileKey, OWNER)).isFalse();
        assertThat(UserImageType.PROFILE.isOwnedBy(backgroundKey, OWNER)).isFalse();
    }

    @ParameterizedTest
    @EnumSource(UserImageType.class)
    @DisplayName("경로 조작으로 자기 prefix를 흉내 낸 key는 인정하지 않는다")
    void traversalKeyIsNotOwned(UserImageType type) {
        // given -> 클라이언트가 보낸 문자열이므로 이런 값이 올 수 있다
        String key = "%s../%s/stolen.jpg".formatted(type.prefixOf(OTHER), OWNER);

        // when & then
        assertThat(type.isOwnedBy(key, OWNER)).isFalse();
    }

    @ParameterizedTest
    @EnumSource(UserImageType.class)
    @DisplayName("prefix가 일부만 겹치는 key는 인정하지 않는다")
    void partialPrefixIsNotOwned(UserImageType type) {
        // given -> 구분자 없이 이어 붙여 앞부분만 같은 경우
        String key = type.prefixOf(OWNER).replaceFirst("/$", "abc/photo.jpg");

        // when & then
        assertThat(type.isOwnedBy(key, OWNER)).isFalse();
    }

    @Test
    @DisplayName("keyOf는 유저에서 자기 종류의 key만 꺼낸다")
    void keyOfReadsOwnTypeKey() {
        // given -> 두 key를 다르게 넣어 서로 바뀌지 않는지 본다
        String profileKey = UserImageType.PROFILE.createKey(OWNER, IMAGE_ID, ImageContentType.JPEG);
        String backgroundKey = UserImageType.BACKGROUND.createKey(OWNER, IMAGE_ID, ImageContentType.PNG);
        User user = new User(OWNER, "user@example.com", PASSWORD_HASH, true,
                profileKey, backgroundKey, ProfileVisibility.PUBLIC, "");

        // when & then
        assertThat(UserImageType.PROFILE.keyOf(user)).contains(profileKey);
        assertThat(UserImageType.BACKGROUND.keyOf(user)).contains(backgroundKey);
    }

    @ParameterizedTest
    @EnumSource(UserImageType.class)
    @DisplayName("사진이 없으면 keyOf는 빈 Optional이다")
    void keyOfIsEmptyWithoutImage(UserImageType type) {
        // given
        User user = new User(OWNER, "user@example.com", PASSWORD_HASH);

        // when & then
        assertThat(type.keyOf(user)).isEmpty();
    }

    @Test
    @DisplayName("종류마다 자기 에러 코드의 예외를 만든다")
    void exceptionsMatchType() {
        // when & then -> 응답 code가 INVALID_PROFILE_IMAGE / INVALID_BACKGROUND_IMAGE로 갈린다
        assertThat(UserImageType.PROFILE.invalid()).isInstanceOf(InvalidProfileImageException.class);
        assertThat(UserImageType.PROFILE.notUploaded()).isInstanceOf(ProfileImageNotUploadedException.class);
        assertThat(UserImageType.BACKGROUND.invalid()).isInstanceOf(InvalidBackgroundImageException.class);
        assertThat(UserImageType.BACKGROUND.notUploaded()).isInstanceOf(BackgroundImageNotUploadedException.class);
    }

    @Test
    @DisplayName("예외는 호출마다 새로 만든다")
    void exceptionsAreNewEachCall() {
        // when & then -> 하나를 돌려쓰면 스택 트레이스가 처음 만든 자리로 고정된다
        assertThat(UserImageType.PROFILE.invalid()).isNotSameAs(UserImageType.PROFILE.invalid());
    }
}
