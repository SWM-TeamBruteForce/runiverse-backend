package com.runiverse.running_service.unit_test.user.application;

import com.github.f4b6a3.uuid.UuidCreator;
import com.runiverse.running_service.application.user.exception.ProfileNotFoundException;
import com.runiverse.running_service.application.user.port.out.GenerateViewUrlPort;
import com.runiverse.running_service.application.user.port.out.LoadUserByIdPort;
import com.runiverse.running_service.application.user.query.profileimage.GetProfileImageUrlHandler;
import com.runiverse.running_service.application.user.query.profileimage.GetProfileImageUrlQuery;
import com.runiverse.running_service.application.user.query.profileimage.GetProfileImageUrlResult;
import com.runiverse.running_service.domain.user.User;
import com.runiverse.running_service.domain.user.vo.ProfileVisibility;
import com.runiverse.running_service.domain.common.vo.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("프로필 이미지 조회 URL 발급 단위 테스트")
public class GetProfileImageUrlHandlerTest {

    // PasswordHash VO가 Argon2id 형식만 허용하므로 형식에 맞는 값을 쓴다
    private static final String PASSWORD_HASH =
            "$argon2id$v=19$m=16384,t=2,p=1$c29tZXNhbHQ$aGFzaHZhbHVl";
    private static final String VIEW_URL =
            "https://runiverse-test-bucket.s3.ap-northeast-2.amazonaws.com/profiles/photo.jpg?X-Amz-Signature=test";

    @Mock
    private LoadUserByIdPort loadUserByIdPort;

    @Mock
    private GenerateViewUrlPort generateViewUrlPort;

    @InjectMocks
    private GetProfileImageUrlHandler handler;

    private static User userWith(UUID userId, String profileImageKey) {
        return new User(userId, "runner@runiverse.com", PASSWORD_HASH, true,
                profileImageKey, ProfileVisibility.PUBLIC, "");
    }

    private static String keyOf(UUID userId) {
        return "profiles/" + userId + "/019ffa54-917f-7477-9482-5792597ef3b0.jpg";
    }

    @Test
    @DisplayName("프로필 이미지가 있으면 저장된 key로 조회 URL을 발급한다")
    void issuesViewUrlForStoredKey() {
        // given
        UUID userId = UuidCreator.getTimeOrderedEpoch();
        String key = keyOf(userId);
        when(loadUserByIdPort.loadById(new UserId(userId))).thenReturn(Optional.of(userWith(userId, key)));
        when(generateViewUrlPort.generate(key)).thenReturn(VIEW_URL);

        // when
        GetProfileImageUrlResult result = handler.handle(new GetProfileImageUrlQuery(userId));

        // then
        assertThat(result.userId()).isEqualTo(userId);
        assertThat(result.profileImageUrl()).isEqualTo(VIEW_URL);
        verify(generateViewUrlPort).generate(key);
    }

    @Test
    @DisplayName("본인이 아닌 사용자의 프로필 이미지도 발급한다")
    void issuesViewUrlForOtherUser() {
        // given -> 이 유스케이스는 소유자 제한이 없다. 남의 프로필 사진도 보여야 한다
        UUID otherUserId = UuidCreator.getTimeOrderedEpoch();
        String key = keyOf(otherUserId);
        when(loadUserByIdPort.loadById(new UserId(otherUserId)))
                .thenReturn(Optional.of(userWith(otherUserId, key)));
        when(generateViewUrlPort.generate(key)).thenReturn(VIEW_URL);

        // when
        GetProfileImageUrlResult result = handler.handle(new GetProfileImageUrlQuery(otherUserId));

        // then
        assertThat(result.profileImageUrl()).isEqualTo(VIEW_URL);
    }

    @Test
    @DisplayName("프로필 이미지가 없으면 URL 없이 반환하고 발급을 시도하지 않는다")
    void returnsNullUrlWhenImageIsNotRegistered() {
        // given -> erd상 profile_image_key는 미등록이면 null이다
        UUID userId = UuidCreator.getTimeOrderedEpoch();
        when(loadUserByIdPort.loadById(new UserId(userId))).thenReturn(Optional.of(userWith(userId, null)));

        // when
        GetProfileImageUrlResult result = handler.handle(new GetProfileImageUrlQuery(userId));

        // then -> 이미지가 없는 건 정상 상태라 예외가 아니다
        assertThat(result.userId()).isEqualTo(userId);
        assertThat(result.profileImageUrl()).isNull();
        verifyNoInteractions(generateViewUrlPort);
    }

    @Test
    @DisplayName("사용자가 없으면 발급하지 않고 예외를 던진다")
    void throwsWhenUserNotFound() {
        // given
        UUID unknownUserId = UuidCreator.getTimeOrderedEpoch();
        when(loadUserByIdPort.loadById(new UserId(unknownUserId))).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> handler.handle(new GetProfileImageUrlQuery(unknownUserId)))
                .isInstanceOf(ProfileNotFoundException.class);
        verifyNoInteractions(generateViewUrlPort);
    }

    @Test
    @DisplayName("조회할 사용자 식별자를 그대로 포트에 넘긴다")
    void looksUpRequestedUser() {
        // given
        UUID userId = UuidCreator.getTimeOrderedEpoch();
        when(loadUserByIdPort.loadById(any())).thenReturn(Optional.of(userWith(userId, null)));

        // when
        handler.handle(new GetProfileImageUrlQuery(userId));

        // then
        verify(loadUserByIdPort).loadById(new UserId(userId));
    }
}
