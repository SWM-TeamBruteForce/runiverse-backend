package com.runiverse.running_service.unit_test.user.application;

import com.github.f4b6a3.uuid.UuidCreator;
import com.runiverse.running_service.application.common.port.out.LoadUserByIdPort;
import com.runiverse.running_service.application.user.common.UserImageUrlResolver;
import com.runiverse.running_service.application.user.exception.ProfileNotFoundException;
import com.runiverse.running_service.application.user.port.out.GenerateViewUrlPort;
import com.runiverse.running_service.application.user.query.backgroundimage.GetBackgroundImageUrlHandler;
import com.runiverse.running_service.application.user.query.backgroundimage.GetBackgroundImageUrlQuery;
import com.runiverse.running_service.application.user.query.backgroundimage.GetBackgroundImageUrlResult;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.user.User;
import com.runiverse.running_service.domain.user.vo.ProfileVisibility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
@DisplayName("배경 이미지 조회 URL 발급 단위 테스트")
public class GetBackgroundImageUrlHandlerTest {

    // PasswordHash VO가 Argon2id 형식만 허용하므로 형식에 맞는 값을 쓴다
    private static final String PASSWORD_HASH =
            "$argon2id$v=19$m=16384,t=2,p=1$c29tZXNhbHQ$aGFzaHZhbHVl";
    private static final String VIEW_URL =
            "https://runiverse-test-bucket.s3.ap-northeast-2.amazonaws.com/backgrounds/photo.jpg?X-Amz-Signature=test";

    @Mock
    private LoadUserByIdPort loadUserByIdPort;

    @Mock
    private GenerateViewUrlPort generateViewUrlPort;

    private GetBackgroundImageUrlHandler handler;

    // URL 변환은 공유 컴포넌트가 맡는다 — 실물을 끼워 서명 포트와의 상호작용까지 그대로 본다
    @BeforeEach
    void setUp() {
        handler = new GetBackgroundImageUrlHandler(loadUserByIdPort, new UserImageUrlResolver(generateViewUrlPort));
    }

    private static User userWith(UUID userId, String backgroundImageKey) {
        return new User(userId, "runner@runiverse.com", PASSWORD_HASH, true,
                null, backgroundImageKey, ProfileVisibility.PUBLIC, "");
    }

    private static String keyOf(UUID userId) {
        return "backgrounds/" + userId + "/019ffa54-917f-7477-9482-5792597ef3b0.jpg";
    }

    @Test
    @DisplayName("배경 이미지가 있으면 저장된 key로 조회 URL을 발급한다")
    void issuesViewUrlForStoredKey() {
        // given
        UUID userId = UuidCreator.getTimeOrderedEpoch();
        String key = keyOf(userId);
        when(loadUserByIdPort.loadById(new UserId(userId))).thenReturn(Optional.of(userWith(userId, key)));
        when(generateViewUrlPort.generate(key)).thenReturn(VIEW_URL);

        // when
        GetBackgroundImageUrlResult result = handler.handle(new GetBackgroundImageUrlQuery(userId));

        // then
        assertThat(result.userId()).isEqualTo(userId);
        assertThat(result.backgroundImageUrl()).isEqualTo(VIEW_URL);
        verify(generateViewUrlPort).generate(key);
    }

    @Test
    @DisplayName("본인이 아닌 사용자의 배경 이미지도 발급한다")
    void issuesViewUrlForOtherUser() {
        // given -> 이 유스케이스는 소유자 제한이 없다. 남의 배경도 보여야 한다
        UUID otherUserId = UuidCreator.getTimeOrderedEpoch();
        String key = keyOf(otherUserId);
        when(loadUserByIdPort.loadById(new UserId(otherUserId)))
                .thenReturn(Optional.of(userWith(otherUserId, key)));
        when(generateViewUrlPort.generate(key)).thenReturn(VIEW_URL);

        // when
        GetBackgroundImageUrlResult result = handler.handle(new GetBackgroundImageUrlQuery(otherUserId));

        // then
        assertThat(result.backgroundImageUrl()).isEqualTo(VIEW_URL);
    }

    @Test
    @DisplayName("프로필 사진만 있으면 배경 URL은 없다")
    void ignoresProfileImageKey() {
        // given -> 프로필 key가 배경 자리로 새지 않아야 한다
        UUID userId = UuidCreator.getTimeOrderedEpoch();
        User user = new User(userId, "runner@runiverse.com", PASSWORD_HASH, true,
                "profiles/" + userId + "/me.jpg", null, ProfileVisibility.PUBLIC, "");
        when(loadUserByIdPort.loadById(new UserId(userId))).thenReturn(Optional.of(user));

        // when
        GetBackgroundImageUrlResult result = handler.handle(new GetBackgroundImageUrlQuery(userId));

        // then
        assertThat(result.backgroundImageUrl()).isNull();
        verifyNoInteractions(generateViewUrlPort);
    }

    @Test
    @DisplayName("배경 이미지가 없으면 URL 없이 반환하고 발급을 시도하지 않는다")
    void returnsNullUrlWhenImageIsNotRegistered() {
        // given -> erd상 background_image_key는 미등록이면 null이다
        UUID userId = UuidCreator.getTimeOrderedEpoch();
        when(loadUserByIdPort.loadById(new UserId(userId))).thenReturn(Optional.of(userWith(userId, null)));

        // when
        GetBackgroundImageUrlResult result = handler.handle(new GetBackgroundImageUrlQuery(userId));

        // then -> 이미지가 없는 건 정상 상태라 예외가 아니다
        assertThat(result.userId()).isEqualTo(userId);
        assertThat(result.backgroundImageUrl()).isNull();
        verifyNoInteractions(generateViewUrlPort);
    }

    @Test
    @DisplayName("사용자가 없으면 발급하지 않고 예외를 던진다")
    void throwsWhenUserNotFound() {
        // given
        UUID unknownUserId = UuidCreator.getTimeOrderedEpoch();
        when(loadUserByIdPort.loadById(new UserId(unknownUserId))).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> handler.handle(new GetBackgroundImageUrlQuery(unknownUserId)))
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
        handler.handle(new GetBackgroundImageUrlQuery(userId));

        // then
        verify(loadUserByIdPort).loadById(new UserId(userId));
    }
}
