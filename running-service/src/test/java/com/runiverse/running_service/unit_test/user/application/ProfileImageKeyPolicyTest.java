package com.runiverse.running_service.unit_test.user.application;

import com.runiverse.running_service.application.user.command.profileimage.ProfileImageContentType;
import com.runiverse.running_service.application.user.command.profileimage.ProfileImageKeyPolicy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("프로필 이미지 key 정책 단위 테스트")
public class ProfileImageKeyPolicyTest {

    private static final UUID OWNER = UUID.fromString("019ff918-a5ac-75b6-b20b-350454888411");
    private static final UUID IMAGE_ID = UUID.fromString("019ffa54-917f-7477-9482-5792597ef3b0");
    private static final UUID OTHER = UUID.fromString("00000000-0000-0000-0000-000000000009");

    @Test
    @DisplayName("key는 소유자 prefix 아래에 mimeType으로 정한 확장자로 만든다")
    void createBuildsKeyUnderOwnerPrefix() {
        // when
        String key = ProfileImageKeyPolicy.create(OWNER, IMAGE_ID, ProfileImageContentType.PNG).value();

        // then
        assertThat(key).isEqualTo("profiles/%s/%s.png".formatted(OWNER, IMAGE_ID));
    }

    @Test
    @DisplayName("자기 prefix로 만든 key는 소유자로 인정한다")
    void ownKeyIsOwned() {
        // given
        String key = ProfileImageKeyPolicy.create(OWNER, IMAGE_ID, ProfileImageContentType.JPEG).value();

        // when & then
        assertThat(ProfileImageKeyPolicy.isOwnedBy(key, OWNER)).isTrue();
    }

    @Test
    @DisplayName("다른 사용자의 key는 소유자로 인정하지 않는다")
    void otherUsersKeyIsNotOwned() {
        // given
        String key = ProfileImageKeyPolicy.create(OWNER, IMAGE_ID, ProfileImageContentType.JPEG).value();

        // when & then -> 남의 key를 자기 프로필로 붙이는 것을 막는 지점이다
        assertThat(ProfileImageKeyPolicy.isOwnedBy(key, OTHER)).isFalse();
    }

    @Test
    @DisplayName("경로 조작으로 자기 prefix를 흉내 낸 key는 인정하지 않는다")
    void traversalKeyIsNotOwned() {
        // given -> 클라이언트가 보낸 문자열이므로 이런 값이 올 수 있다
        String key = "profiles/%s/../%s/stolen.jpg".formatted(OTHER, OWNER);

        // when & then
        assertThat(ProfileImageKeyPolicy.isOwnedBy(key, OWNER)).isFalse();
    }

    @Test
    @DisplayName("prefix가 일부만 겹치는 key는 인정하지 않는다")
    void partialPrefixIsNotOwned() {
        // given -> 구분자 없이 이어 붙여 앞부분만 같은 경우
        String key = "profiles/%sabc/photo.jpg".formatted(OWNER);

        // when & then
        assertThat(ProfileImageKeyPolicy.isOwnedBy(key, OWNER)).isFalse();
    }
}
