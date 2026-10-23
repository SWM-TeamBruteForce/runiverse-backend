package com.runiverse.running_service.integration_test.user;

import com.github.f4b6a3.uuid.UuidCreator;
import com.runiverse.running_service.application.user.command.backgroundimage.ChangeBackgroundImageCommand;
import com.runiverse.running_service.application.user.command.backgroundimage.ChangeBackgroundImageHandler;
import com.runiverse.running_service.application.user.command.backgroundimage.ChangeBackgroundImageResult;
import com.runiverse.running_service.application.user.common.ImageContentType;
import com.runiverse.running_service.application.user.common.UploadedImageVerifier;
import com.runiverse.running_service.application.user.common.UserImageType;
import com.runiverse.running_service.application.user.exception.InvalidBackgroundImageException;
import com.runiverse.running_service.application.user.exception.BackgroundImageNotUploadedException;
import com.runiverse.running_service.integration_test.fake.FakeUploadedImageStore;
import com.runiverse.running_service.integration_test.fake.InMemoryBackgroundImageStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("배경 이미지 변경 통합 테스트")
public class ChangeBackgroundImageIntegrationTest {

    private static final String JPEG = "image/jpeg";
    private static final long MAX_SIZE_BYTES = 10L * 1024 * 1024;
    private static final long SIZE_BYTES = 20_480L;

    private UUID userId;
    private String key;
    private FakeUploadedImageStore uploadedImageStore;
    private InMemoryBackgroundImageStore backgroundImageStore;
    private ChangeBackgroundImageHandler handler;

    @BeforeEach
    void setUp() {
        userId = UuidCreator.getTimeOrderedEpoch();
        key = keyOf(userId);
        uploadedImageStore = new FakeUploadedImageStore();
        backgroundImageStore = new InMemoryBackgroundImageStore();
        handler = new ChangeBackgroundImageHandler(
                new UploadedImageVerifier(uploadedImageStore),  // LoadUploadedImagePort
                backgroundImageStore    // UpdateBackgroundImagePort
        );
    }

    private static String keyOf(UUID owner) {
        return UserImageType.BACKGROUND.createKey(
                owner, UuidCreator.getTimeOrderedEpoch(), ImageContentType.JPEG);
    }

    @Test
    @DisplayName("업로드된 이미지의 key를 사용자에게 저장한다")
    void changeBackgroundImageSuccess() {
        // given
        uploadedImageStore.register(key, SIZE_BYTES, JPEG);

        // when
        ChangeBackgroundImageResult result = handler.handle(new ChangeBackgroundImageCommand(userId, key));

        // then
        assertThat(result.userId()).isEqualTo(userId);
        assertThat(result.backgroundImageKey()).isEqualTo(key);
        assertThat(backgroundImageStore.keyOf(userId)).contains(key);
    }

    @Test
    @DisplayName("다른 사용자의 key를 보내면 저장하지 않고 막는다")
    void otherUsersKeyIsRejected() {
        // given -> 남의 prefix로 만든 key. 실제로 업로드까지 되어 있어도 막아야 한다
        String stolenKey = keyOf(UuidCreator.getTimeOrderedEpoch());
        uploadedImageStore.register(stolenKey, SIZE_BYTES, JPEG);

        // when & then
        assertThatThrownBy(() -> handler.handle(new ChangeBackgroundImageCommand(userId, stolenKey)))
                .isInstanceOf(InvalidBackgroundImageException.class);
        assertThat(backgroundImageStore.isEmpty()).isTrue();
    }

    @Test
    @DisplayName("자기 프로필 사진 key를 보내면 배경으로 저장하지 않고 막는다")
    void ownProfileKeyIsRejected() {
        // given -> 본인 것이고 실제로 올라가 있어도 prefix가 달라 배경이 될 수 없다
        String profileKey = UserImageType.PROFILE.createKey(
                userId, UuidCreator.getTimeOrderedEpoch(), ImageContentType.JPEG);
        uploadedImageStore.register(profileKey, SIZE_BYTES, JPEG);

        // when & then
        assertThatThrownBy(() -> handler.handle(new ChangeBackgroundImageCommand(userId, profileKey)))
                .isInstanceOf(InvalidBackgroundImageException.class);
        assertThat(backgroundImageStore.isEmpty()).isTrue();
    }

    @Test
    @DisplayName("업로드하지 않은 key를 보내면 저장하지 않고 막는다")
    void notUploadedKeyIsRejected() {
        // when & then -> 발급만 받고 S3에 올리지 않은 경우다
        assertThatThrownBy(() -> handler.handle(new ChangeBackgroundImageCommand(userId, key)))
                .isInstanceOf(BackgroundImageNotUploadedException.class);
        assertThat(backgroundImageStore.isEmpty()).isTrue();
    }

    @Test
    @DisplayName("10MB를 넘는 이미지는 저장하지 않고 막는다")
    void tooLargeImageIsRejected() {
        // given -> presigned URL은 크기를 강제하지만 저장된 실물로 한 번 더 본다
        uploadedImageStore.register(key, MAX_SIZE_BYTES + 1, JPEG);

        // when & then
        assertThatThrownBy(() -> handler.handle(new ChangeBackgroundImageCommand(userId, key)))
                .isInstanceOf(InvalidBackgroundImageException.class);
        assertThat(backgroundImageStore.isEmpty()).isTrue();
    }

    @Test
    @DisplayName("정확히 10MB인 이미지는 통과한다")
    void exactlyMaxSizeIsAccepted() {
        // given
        uploadedImageStore.register(key, MAX_SIZE_BYTES, JPEG);

        // when
        handler.handle(new ChangeBackgroundImageCommand(userId, key));

        // then
        assertThat(backgroundImageStore.keyOf(userId)).contains(key);
    }

    @Test
    @DisplayName("허용하지 않는 형식으로 올라간 이미지는 저장하지 않고 막는다")
    void unsupportedContentTypeIsRejected() {
        // given -> 서명을 우회해 다른 형식을 올린 경우를 가정한다
        uploadedImageStore.register(key, SIZE_BYTES, "image/gif");

        // when & then
        assertThatThrownBy(() -> handler.handle(new ChangeBackgroundImageCommand(userId, key)))
                .isInstanceOf(InvalidBackgroundImageException.class);
        assertThat(backgroundImageStore.isEmpty()).isTrue();
    }
}
