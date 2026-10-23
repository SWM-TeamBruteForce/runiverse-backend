package com.runiverse.running_service.integration_test.user;

import com.github.f4b6a3.uuid.UuidCreator;
import com.runiverse.running_service.application.auth.command.signup.SignUpCommand;
import com.runiverse.running_service.application.auth.command.signup.SignUpHandler;
import com.runiverse.running_service.application.user.command.backgroundimage.ChangeBackgroundImageCommand;
import com.runiverse.running_service.application.user.command.backgroundimage.ChangeBackgroundImageHandler;
import com.runiverse.running_service.application.user.command.backgroundimage.DeleteBackgroundImageCommand;
import com.runiverse.running_service.application.user.command.backgroundimage.DeleteBackgroundImageHandler;
import com.runiverse.running_service.application.user.command.profileimage.ChangeProfileImageCommand;
import com.runiverse.running_service.application.user.command.profileimage.ChangeProfileImageHandler;
import com.runiverse.running_service.application.user.common.ImageContentType;
import com.runiverse.running_service.application.user.common.UploadedImageVerifier;
import com.runiverse.running_service.application.user.common.UserImageType;
import com.runiverse.running_service.application.user.common.UserImageUrlResolver;
import com.runiverse.running_service.application.user.exception.ProfileNotFoundException;
import com.runiverse.running_service.application.user.query.backgroundimage.GetBackgroundImageUrlHandler;
import com.runiverse.running_service.application.user.query.backgroundimage.GetBackgroundImageUrlQuery;
import com.runiverse.running_service.application.user.query.backgroundimage.GetBackgroundImageUrlResult;
import com.runiverse.running_service.integration_test.IntegrationTestSupport;
import com.runiverse.running_service.integration_test.fake.FakeUploadedImageStore;
import com.runiverse.running_service.integration_test.fake.FakeViewUrlGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("배경 사진 URL 조회 통합 테스트")
public class GetBackgroundImageUrlIntegrationTest extends IntegrationTestSupport {

    private static final String EMAIL = "runner@runiverse.com";
    private static final String OTHER_EMAIL = "other@runiverse.com";
    private static final String PASSWORD = "Password123!";
    private static final String JPEG = "image/jpeg";
    private static final long SIZE_BYTES = 20_480L;

    private SignUpHandler signUpHandler;
    private FakeUploadedImageStore uploadedImageStore;
    private FakeViewUrlGenerator viewUrlGenerator;
    private ChangeBackgroundImageHandler changeBackgroundImageHandler;
    private ChangeProfileImageHandler changeProfileImageHandler;
    private DeleteBackgroundImageHandler deleteBackgroundImageHandler;
    private GetBackgroundImageUrlHandler handler;

    @BeforeEach
    void setUp() {
        signUpHandler = newSignUpHandler();
        uploadedImageStore = new FakeUploadedImageStore();
        viewUrlGenerator = new FakeViewUrlGenerator();
        UploadedImageVerifier verifier = new UploadedImageVerifier(uploadedImageStore);  // LoadUploadedImagePort
        changeBackgroundImageHandler = new ChangeBackgroundImageHandler(
                verifier,
                userStore            // UpdateBackgroundImagePort
        );
        changeProfileImageHandler = new ChangeProfileImageHandler(
                verifier,
                userStore            // UpdateProfileImagePort
        );
        deleteBackgroundImageHandler = new DeleteBackgroundImageHandler(
                userStore            // ClearBackgroundImagePort
        );
        handler = new GetBackgroundImageUrlHandler(
                userStore,           // LoadUserByIdPort
                new UserImageUrlResolver(viewUrlGenerator)  // GenerateViewUrlPort
        );
    }

    private UUID signUp(String email) {
        return signUpHandler.handle(new SignUpCommand(issueVerificationTicket(email), PASSWORD)).userId();
    }

    // 업로드까지 마친 key를 반영한다 — 발급만 받은 key는 변경 핸들러가 막는다
    private String registerImage(UserImageType type, UUID userId) {
        String key = type.createKey(userId, UuidCreator.getTimeOrderedEpoch(), ImageContentType.JPEG);
        uploadedImageStore.register(key, SIZE_BYTES, JPEG);
        if (type == UserImageType.BACKGROUND) {
            changeBackgroundImageHandler.handle(new ChangeBackgroundImageCommand(userId, key));
        } else {
            changeProfileImageHandler.handle(new ChangeProfileImageCommand(userId, key));
        }
        return key;
    }

    private GetBackgroundImageUrlResult imageOf(UUID userId) {
        return handler.handle(new GetBackgroundImageUrlQuery(userId));
    }

    @Test
    @DisplayName("배경을 등록하지 않은 사용자는 URL 없이 답하고 서명도 하지 않는다")
    void returnsNullWithoutBackgroundImage() {
        // given
        UUID userId = signUp(EMAIL);

        // when
        GetBackgroundImageUrlResult result = imageOf(userId);

        // then -> 기본 배경은 클라가 그린다
        assertThat(result.userId()).isEqualTo(userId);
        assertThat(result.backgroundImageUrl()).isNull();
        assertThat(viewUrlGenerator.isEmpty()).isTrue();
    }

    @Test
    @DisplayName("배경을 반영하면 그 key로 서명한 URL을 돌려준다")
    void returnsSignedUrlOfRegisteredImage() {
        // given
        UUID userId = signUp(EMAIL);
        String key = registerImage(UserImageType.BACKGROUND, userId);

        // when
        GetBackgroundImageUrlResult result = imageOf(userId);

        // then
        assertThat(result.backgroundImageUrl()).isEqualTo(viewUrlGenerator.urlOf(key));
    }

    @Test
    @DisplayName("프로필 사진만 있으면 배경 URL은 없다")
    void doesNotReturnProfileImageAsBackground() {
        // given -> 프로필 key가 배경 자리로 새면 프로필 사진이 배경으로 그려진다
        UUID userId = signUp(EMAIL);
        registerImage(UserImageType.PROFILE, userId);

        // when & then
        assertThat(imageOf(userId).backgroundImageUrl()).isNull();
    }

    @Test
    @DisplayName("프로필과 배경이 모두 있으면 배경 key로만 서명한다")
    void signsOnlyBackgroundKey() {
        // given
        UUID userId = signUp(EMAIL);
        registerImage(UserImageType.PROFILE, userId);
        String backgroundKey = registerImage(UserImageType.BACKGROUND, userId);

        // when & then -> 프로필을 나중에 바꿔도 배경이 그대로인지도 함께 본다
        registerImage(UserImageType.PROFILE, userId);
        assertThat(imageOf(userId).backgroundImageUrl()).isEqualTo(viewUrlGenerator.urlOf(backgroundKey));
    }

    @Test
    @DisplayName("배경을 삭제하면 다시 URL 없이 답한다")
    void returnsNullAfterBackgroundCleared() {
        // given
        UUID userId = signUp(EMAIL);
        registerImage(UserImageType.BACKGROUND, userId);

        // when
        deleteBackgroundImageHandler.handle(new DeleteBackgroundImageCommand(userId));

        // then
        assertThat(imageOf(userId).backgroundImageUrl()).isNull();
    }

    @Test
    @DisplayName("다른 사용자의 배경이 섞이지 않는다")
    void doesNotLeakOtherUsersImage() {
        // given -> 인증 없이 남의 배경을 조회하는 경로라 대상이 정확해야 한다
        UUID userId = signUp(EMAIL);
        UUID otherUserId = signUp(OTHER_EMAIL);
        String otherKey = registerImage(UserImageType.BACKGROUND, otherUserId);

        // when & then
        assertThat(imageOf(userId).backgroundImageUrl()).isNull();
        assertThat(imageOf(otherUserId).backgroundImageUrl()).isEqualTo(viewUrlGenerator.urlOf(otherKey));
    }

    @Test
    @DisplayName("가입한 적 없는 사용자는 조회할 수 없다")
    void throwsForUnknownUser() {
        // when & then -> 탈퇴한 사용자도 같은 경로로 걸러진다
        assertThatThrownBy(() -> imageOf(UuidCreator.getTimeOrderedEpoch()))
                .isInstanceOf(ProfileNotFoundException.class);
        assertThat(viewUrlGenerator.isEmpty()).isTrue();
    }
}
