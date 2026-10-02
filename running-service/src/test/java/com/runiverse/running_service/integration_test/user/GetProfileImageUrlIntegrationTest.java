package com.runiverse.running_service.integration_test.user;

import com.github.f4b6a3.uuid.UuidCreator;
import com.runiverse.running_service.application.auth.command.signup.SignUpCommand;
import com.runiverse.running_service.application.auth.command.signup.SignUpHandler;
import com.runiverse.running_service.application.user.command.profileimage.ChangeProfileImageCommand;
import com.runiverse.running_service.application.user.command.profileimage.ChangeProfileImageHandler;
import com.runiverse.running_service.application.user.command.profileimage.DeleteProfileImageCommand;
import com.runiverse.running_service.application.user.command.profileimage.DeleteProfileImageHandler;
import com.runiverse.running_service.application.user.command.profileimage.ProfileImageContentType;
import com.runiverse.running_service.application.user.command.profileimage.ProfileImageKeyPolicy;
import com.runiverse.running_service.application.user.exception.ProfileNotFoundException;
import com.runiverse.running_service.application.user.query.profileimage.GetProfileImageUrlHandler;
import com.runiverse.running_service.application.user.query.profileimage.GetProfileImageUrlQuery;
import com.runiverse.running_service.application.user.query.profileimage.GetProfileImageUrlResult;
import com.runiverse.running_service.integration_test.IntegrationTestSupport;
import com.runiverse.running_service.integration_test.fake.FakeUploadedImageStore;
import com.runiverse.running_service.integration_test.fake.FakeViewUrlGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("프로필 사진 URL 조회 통합 테스트")
public class GetProfileImageUrlIntegrationTest extends IntegrationTestSupport {

    private static final String EMAIL = "runner@runiverse.com";
    private static final String OTHER_EMAIL = "other@runiverse.com";
    private static final String PASSWORD = "Password123!";
    private static final String JPEG = "image/jpeg";
    private static final long SIZE_BYTES = 20_480L;

    private SignUpHandler signUpHandler;
    private FakeUploadedImageStore uploadedImageStore;
    private FakeViewUrlGenerator viewUrlGenerator;
    private ChangeProfileImageHandler changeProfileImageHandler;
    private DeleteProfileImageHandler deleteProfileImageHandler;
    private GetProfileImageUrlHandler handler;

    @BeforeEach
    void setUp() {
        signUpHandler = newSignUpHandler();
        uploadedImageStore = new FakeUploadedImageStore();
        viewUrlGenerator = new FakeViewUrlGenerator();
        changeProfileImageHandler = new ChangeProfileImageHandler(
                uploadedImageStore,  // LoadUploadedImagePort
                userStore            // UpdateProfileImagePort
        );
        deleteProfileImageHandler = new DeleteProfileImageHandler(
                userStore            // ClearProfileImagePort
        );
        handler = new GetProfileImageUrlHandler(
                userStore,           // LoadUserByIdPort
                viewUrlGenerator     // GenerateViewUrlPort
        );
    }

    private UUID signUp(String email) {
        return signUpHandler.handle(new SignUpCommand(issueVerificationTicket(email), PASSWORD)).userId();
    }

    // 업로드까지 마친 key를 반영한다 — 발급만 받은 key는 변경 핸들러가 막는다
    private String registerProfileImage(UUID userId) {
        String key = ProfileImageKeyPolicy.create(
                userId, UuidCreator.getTimeOrderedEpoch(), ProfileImageContentType.JPEG).value();
        uploadedImageStore.register(key, SIZE_BYTES, JPEG);
        changeProfileImageHandler.handle(new ChangeProfileImageCommand(userId, key));
        return key;
    }

    private GetProfileImageUrlResult imageOf(UUID userId) {
        return handler.handle(new GetProfileImageUrlQuery(userId));
    }

    @Test
    @DisplayName("사진을 등록하지 않은 사용자는 URL 없이 답하고 서명도 하지 않는다")
    void returnsNullWithoutProfileImage() {
        // given
        UUID userId = signUp(EMAIL);

        // when
        GetProfileImageUrlResult result = imageOf(userId);

        // then
        assertThat(result.userId()).isEqualTo(userId);
        assertThat(result.profileImageUrl()).isNull();
        assertThat(viewUrlGenerator.isEmpty()).isTrue();
    }

    @Test
    @DisplayName("사진을 반영하면 그 key로 서명한 URL을 돌려준다")
    void returnsSignedUrlOfRegisteredImage() {
        // given
        UUID userId = signUp(EMAIL);
        String key = registerProfileImage(userId);

        // when
        GetProfileImageUrlResult result = imageOf(userId);

        // then
        assertThat(result.profileImageUrl()).isEqualTo(viewUrlGenerator.urlOf(key));
    }

    @Test
    @DisplayName("사진을 삭제하면 다시 URL 없이 답한다")
    void returnsNullAfterProfileImageDeleted() {
        // given
        UUID userId = signUp(EMAIL);
        registerProfileImage(userId);

        // when
        deleteProfileImageHandler.handle(new DeleteProfileImageCommand(userId));

        // then
        assertThat(imageOf(userId).profileImageUrl()).isNull();
    }

    @Test
    @DisplayName("다른 사용자의 사진이 섞이지 않는다")
    void doesNotLeakOtherUsersImage() {
        // given -> 인증 없이 남의 사진을 조회하는 경로라 대상이 정확해야 한다
        UUID userId = signUp(EMAIL);
        UUID otherUserId = signUp(OTHER_EMAIL);
        String otherKey = registerProfileImage(otherUserId);

        // when & then
        assertThat(imageOf(userId).profileImageUrl()).isNull();
        assertThat(imageOf(otherUserId).profileImageUrl()).isEqualTo(viewUrlGenerator.urlOf(otherKey));
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
