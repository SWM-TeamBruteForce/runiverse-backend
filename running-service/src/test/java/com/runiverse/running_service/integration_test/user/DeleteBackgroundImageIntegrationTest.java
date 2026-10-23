package com.runiverse.running_service.integration_test.user;

import com.github.f4b6a3.uuid.UuidCreator;
import com.runiverse.running_service.application.user.command.backgroundimage.ChangeBackgroundImageCommand;
import com.runiverse.running_service.application.user.command.backgroundimage.ChangeBackgroundImageHandler;
import com.runiverse.running_service.application.user.command.backgroundimage.DeleteBackgroundImageCommand;
import com.runiverse.running_service.application.user.command.backgroundimage.DeleteBackgroundImageHandler;
import com.runiverse.running_service.application.user.common.ImageContentType;
import com.runiverse.running_service.application.user.common.UploadedImageVerifier;
import com.runiverse.running_service.application.user.common.UserImageType;
import com.runiverse.running_service.integration_test.fake.FakeUploadedImageStore;
import com.runiverse.running_service.integration_test.fake.InMemoryBackgroundImageStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

@DisplayName("배경 이미지 삭제 통합 테스트")
public class DeleteBackgroundImageIntegrationTest {

    private static final String JPEG = "image/jpeg";
    private static final long SIZE_BYTES = 20_480L;

    private UUID userId;
    private FakeUploadedImageStore uploadedImageStore;
    private InMemoryBackgroundImageStore backgroundImageStore;
    private ChangeBackgroundImageHandler changeHandler;
    private DeleteBackgroundImageHandler deleteHandler;

    @BeforeEach
    void setUp() {
        userId = UuidCreator.getTimeOrderedEpoch();
        uploadedImageStore = new FakeUploadedImageStore();
        backgroundImageStore = new InMemoryBackgroundImageStore();
        changeHandler = new ChangeBackgroundImageHandler(
                new UploadedImageVerifier(uploadedImageStore),  // LoadUploadedImagePort
                backgroundImageStore    // UpdateBackgroundImagePort
        );
        deleteHandler = new DeleteBackgroundImageHandler(
                backgroundImageStore    // ClearBackgroundImagePort
        );
    }

    private static String keyOf(UUID owner) {
        return UserImageType.BACKGROUND.createKey(
                owner, UuidCreator.getTimeOrderedEpoch(), ImageContentType.JPEG);
    }

    // 업로드 확정까지 마친 상태를 만든다
    private String givenBackgroundImageRegistered(UUID owner) {
        String key = keyOf(owner);
        uploadedImageStore.register(key, SIZE_BYTES, JPEG);
        changeHandler.handle(new ChangeBackgroundImageCommand(owner, key));
        return key;
    }

    @Test
    @DisplayName("등록된 배경 이미지를 삭제하면 저장된 key가 사라진다")
    void deleteRegisteredBackgroundImage() {
        // given
        givenBackgroundImageRegistered(userId);

        // when
        deleteHandler.handle(new DeleteBackgroundImageCommand(userId));

        // then
        assertThat(backgroundImageStore.keyOf(userId)).isEmpty();
    }

    @Test
    @DisplayName("이미지를 등록한 적 없어도 삭제는 성공한다")
    void deleteWithoutRegisteredImageSucceeds() {
        // when & then -> 지울 것이 없는 것은 오류가 아니다
        assertThatCode(() -> deleteHandler.handle(new DeleteBackgroundImageCommand(userId)))
                .doesNotThrowAnyException();
        assertThat(backgroundImageStore.keyOf(userId)).isEmpty();
    }

    @Test
    @DisplayName("연달아 삭제해도 같은 결과를 유지한다")
    void deleteIsIdempotent() {
        // given
        givenBackgroundImageRegistered(userId);
        deleteHandler.handle(new DeleteBackgroundImageCommand(userId));

        // when -> 클라이언트 재시도를 가정한다
        assertThatCode(() -> deleteHandler.handle(new DeleteBackgroundImageCommand(userId)))
                .doesNotThrowAnyException();

        // then
        assertThat(backgroundImageStore.keyOf(userId)).isEmpty();
    }

    @Test
    @DisplayName("다른 사용자의 배경 이미지는 그대로 둔다")
    void deleteDoesNotAffectOtherUsers() {
        // given
        givenBackgroundImageRegistered(userId);
        UUID otherUserId = UuidCreator.getTimeOrderedEpoch();
        String otherKey = givenBackgroundImageRegistered(otherUserId);

        // when
        deleteHandler.handle(new DeleteBackgroundImageCommand(userId));

        // then
        assertThat(backgroundImageStore.keyOf(userId)).isEmpty();
        assertThat(backgroundImageStore.keyOf(otherUserId)).contains(otherKey);
    }

    @Test
    @DisplayName("삭제한 뒤 다시 등록할 수 있다")
    void canRegisterAgainAfterDelete() {
        // given
        givenBackgroundImageRegistered(userId);
        deleteHandler.handle(new DeleteBackgroundImageCommand(userId));

        // when -> 배경을 지웠다가 새로 올리는 흐름이다
        String newKey = givenBackgroundImageRegistered(userId);

        // then
        assertThat(backgroundImageStore.keyOf(userId)).contains(newKey);
    }
}
