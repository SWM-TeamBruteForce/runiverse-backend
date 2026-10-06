package com.runiverse.running_service.integration_test.user;

import com.github.f4b6a3.uuid.UuidCreator;
import com.runiverse.running_service.application.user.command.profileimage.ChangeProfileImageCommand;
import com.runiverse.running_service.application.user.command.profileimage.ChangeProfileImageHandler;
import com.runiverse.running_service.application.user.command.profileimage.DeleteProfileImageCommand;
import com.runiverse.running_service.application.user.command.profileimage.DeleteProfileImageHandler;
import com.runiverse.running_service.application.user.command.profileimage.ProfileImageContentType;
import com.runiverse.running_service.application.user.command.profileimage.ProfileImageKeyPolicy;
import com.runiverse.running_service.integration_test.fake.FakeUploadedImageStore;
import com.runiverse.running_service.integration_test.fake.InMemoryProfileImageStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

@DisplayName("프로필 이미지 삭제 통합 테스트")
public class DeleteProfileImageIntegrationTest {

    private static final String JPEG = "image/jpeg";
    private static final long SIZE_BYTES = 20_480L;

    private UUID userId;
    private FakeUploadedImageStore uploadedImageStore;
    private InMemoryProfileImageStore profileImageStore;
    private ChangeProfileImageHandler changeHandler;
    private DeleteProfileImageHandler deleteHandler;

    @BeforeEach
    void setUp() {
        userId = UuidCreator.getTimeOrderedEpoch();
        uploadedImageStore = new FakeUploadedImageStore();
        profileImageStore = new InMemoryProfileImageStore();
        changeHandler = new ChangeProfileImageHandler(
                uploadedImageStore,  // LoadUploadedImagePort
                profileImageStore    // UpdateProfileImagePort
        );
        deleteHandler = new DeleteProfileImageHandler(
                profileImageStore    // ClearProfileImagePort
        );
    }

    private static String keyOf(UUID owner) {
        return ProfileImageKeyPolicy.create(
                owner, UuidCreator.getTimeOrderedEpoch(), ProfileImageContentType.JPEG).value();
    }

    // 업로드 확정까지 마친 상태를 만든다
    private String givenProfileImageRegistered(UUID owner) {
        String key = keyOf(owner);
        uploadedImageStore.register(key, SIZE_BYTES, JPEG);
        changeHandler.handle(new ChangeProfileImageCommand(owner, key));
        return key;
    }

    @Test
    @DisplayName("등록된 프로필 이미지를 삭제하면 저장된 key가 사라진다")
    void deleteRegisteredProfileImage() {
        // given
        givenProfileImageRegistered(userId);

        // when
        deleteHandler.handle(new DeleteProfileImageCommand(userId));

        // then
        assertThat(profileImageStore.keyOf(userId)).isEmpty();
    }

    @Test
    @DisplayName("이미지를 등록한 적 없어도 삭제는 성공한다")
    void deleteWithoutRegisteredImageSucceeds() {
        // when & then -> 지울 것이 없는 것은 오류가 아니다
        assertThatCode(() -> deleteHandler.handle(new DeleteProfileImageCommand(userId)))
                .doesNotThrowAnyException();
        assertThat(profileImageStore.keyOf(userId)).isEmpty();
    }

    @Test
    @DisplayName("연달아 삭제해도 같은 결과를 유지한다")
    void deleteIsIdempotent() {
        // given
        givenProfileImageRegistered(userId);
        deleteHandler.handle(new DeleteProfileImageCommand(userId));

        // when -> 클라이언트 재시도를 가정한다
        assertThatCode(() -> deleteHandler.handle(new DeleteProfileImageCommand(userId)))
                .doesNotThrowAnyException();

        // then
        assertThat(profileImageStore.keyOf(userId)).isEmpty();
    }

    @Test
    @DisplayName("다른 사용자의 프로필 이미지는 그대로 둔다")
    void deleteDoesNotAffectOtherUsers() {
        // given
        givenProfileImageRegistered(userId);
        UUID otherUserId = UuidCreator.getTimeOrderedEpoch();
        String otherKey = givenProfileImageRegistered(otherUserId);

        // when
        deleteHandler.handle(new DeleteProfileImageCommand(userId));

        // then
        assertThat(profileImageStore.keyOf(userId)).isEmpty();
        assertThat(profileImageStore.keyOf(otherUserId)).contains(otherKey);
    }

    @Test
    @DisplayName("삭제한 뒤 다시 등록할 수 있다")
    void canRegisterAgainAfterDelete() {
        // given
        givenProfileImageRegistered(userId);
        deleteHandler.handle(new DeleteProfileImageCommand(userId));

        // when -> 사진을 지웠다가 새로 올리는 흐름이다
        String newKey = givenProfileImageRegistered(userId);

        // then
        assertThat(profileImageStore.keyOf(userId)).contains(newKey);
    }
}
