package com.runiverse.running_service.unit_test.user.application;

import com.runiverse.running_service.application.user.common.UserImageType;
import com.runiverse.running_service.application.user.common.UserImageUrlResolver;
import com.runiverse.running_service.application.user.port.out.GenerateViewUrlPort;
import com.runiverse.running_service.domain.user.User;
import com.runiverse.running_service.domain.user.vo.ProfileVisibility;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("사용자 이미지 URL 변환 단위 테스트")
public class UserImageUrlResolverTest {

    private static final UUID USER_ID = UUID.fromString("019ff918-a5ac-75b6-b20b-350454888411");
    private static final String PASSWORD_HASH =
            "$argon2id$v=19$m=16384,t=2,p=1$c29tZXNhbHQ$aGFzaHZhbHVl";
    private static final String PROFILE_KEY = "profiles/" + USER_ID + "/a.jpg";
    private static final String BACKGROUND_KEY = "backgrounds/" + USER_ID + "/b.png";

    @Mock
    private GenerateViewUrlPort generateViewUrlPort;

    @InjectMocks
    private UserImageUrlResolver userImageUrlResolver;

    @Test
    @DisplayName("종류마다 자기 key로 조회 URL을 만든다")
    void resolveUsesOwnTypeKey() {
        // given -> 두 key를 다르게 넣어 서로 바뀌지 않는지 본다
        User user = new User(USER_ID, "user@example.com", PASSWORD_HASH, true,
                PROFILE_KEY, BACKGROUND_KEY, ProfileVisibility.PUBLIC, "");
        when(generateViewUrlPort.generate(PROFILE_KEY)).thenReturn("https://view/profile");
        when(generateViewUrlPort.generate(BACKGROUND_KEY)).thenReturn("https://view/background");

        // when & then
        assertThat(userImageUrlResolver.resolve(UserImageType.PROFILE, user)).isEqualTo("https://view/profile");
        assertThat(userImageUrlResolver.resolve(UserImageType.BACKGROUND, user)).isEqualTo("https://view/background");
    }

    @ParameterizedTest
    @EnumSource(UserImageType.class)
    @DisplayName("사진이 없으면 null이고 URL을 서명하지 않는다")
    void resolveReturnsNullWithoutImage(UserImageType type) {
        // given
        User user = new User(USER_ID, "user@example.com", PASSWORD_HASH);

        // when & then -> 조회 응답 규칙상 없는 값은 null이다
        assertThat(userImageUrlResolver.resolve(type, user)).isNull();
        verifyNoInteractions(generateViewUrlPort);
    }
}
