package com.runiverse.running_service.integration_test.user;

import com.github.f4b6a3.uuid.UuidCreator;
import com.runiverse.running_service.application.user.command.profileimage.CreateProfileImageUploadUrlCommand;
import com.runiverse.running_service.application.user.command.profileimage.CreateProfileImageUploadUrlHandler;
import com.runiverse.running_service.application.user.command.profileimage.CreateProfileImageUploadUrlResult;
import com.runiverse.running_service.integration_test.fake.FakeProfileImageIdGenerator;
import com.runiverse.running_service.integration_test.fake.FakeUploadUrlGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("프로필 이미지 업로드 URL 발급 통합 테스트")
public class CreateProfileImageUploadUrlIntegrationTest {

    private static final String JPEG = "image/jpeg";
    private static final long FILE_SIZE_BYTES = 20_480L;

    private UUID userId;
    private FakeProfileImageIdGenerator profileImageIdGenerator;
    private FakeUploadUrlGenerator uploadUrlGenerator;
    private CreateProfileImageUploadUrlHandler handler;

    @BeforeEach
    void setUp() {
        // DB를 쓰지 않는 유스케이스라 저장소 fake 없이 포트 두 개만 엮는다
        userId = UuidCreator.getTimeOrderedEpoch();
        profileImageIdGenerator = new FakeProfileImageIdGenerator();
        uploadUrlGenerator = new FakeUploadUrlGenerator();
        handler = new CreateProfileImageUploadUrlHandler(
                profileImageIdGenerator,  // GenerateProfileImageIdPort
                uploadUrlGenerator        // GenerateUploadUrlPort
        );
    }

    private CreateProfileImageUploadUrlCommand command(String mimeType, long fileSizeBytes) {
        return new CreateProfileImageUploadUrlCommand(userId, mimeType, fileSizeBytes);
    }

    @Test
    @DisplayName("발급하면 서버가 만든 key와 그 key의 업로드 URL을 돌려준다")
    void createUploadUrlSuccess() {
        // when
        CreateProfileImageUploadUrlResult result = handler.handle(command(JPEG, FILE_SIZE_BYTES));

        // then
        assertThat(result.profileImageKey())
                .isEqualTo("profiles/%s/00000000-0000-0000-0000-000000000001.jpg".formatted(userId));
        assertThat(result.uploadUrl()).isEqualTo(uploadUrlGenerator.urlOf(result.profileImageKey()));
    }

    @Test
    @DisplayName("key는 요청한 사용자 prefix 아래에 만든다")
    void keyIsUnderRequesterPrefix() {
        // when
        CreateProfileImageUploadUrlResult result = handler.handle(command(JPEG, FILE_SIZE_BYTES));

        // then -> 프로필 수정 때 이 prefix로 소유자를 검증한다
        assertThat(result.profileImageKey()).startsWith("profiles/%s/".formatted(userId));
    }

    @ParameterizedTest
    @CsvSource({
            "image/jpeg, .jpg",
            "image/png, .png",
            "image/webp, .webp"
    })
    @DisplayName("확장자는 클라이언트 파일명이 아니라 mimeType으로 정한다")
    void extensionComesFromMimeType(String mimeType, String extension) {
        // when
        CreateProfileImageUploadUrlResult result = handler.handle(command(mimeType, FILE_SIZE_BYTES));

        // then
        assertThat(result.profileImageKey()).endsWith(extension);
    }

    @Test
    @DisplayName("발급할 때 요청한 형식과 크기를 그대로 서명에 넘긴다")
    void signingValuesArePassedThrough() {
        // when
        handler.handle(command(JPEG, FILE_SIZE_BYTES));

        // then -> 이 두 값이 서명에 들어가야 클라가 다른 타입·크기로 올리지 못한다
        FakeUploadUrlGenerator.IssuedUrl issued = uploadUrlGenerator.last();
        assertThat(issued.contentType()).isEqualTo(JPEG);
        assertThat(issued.sizeBytes()).isEqualTo(FILE_SIZE_BYTES);
    }

    @Test
    @DisplayName("같은 사용자가 다시 발급받아도 key가 겹치지 않는다")
    void keyIsNeverReused() {
        // when
        String first = handler.handle(command(JPEG, FILE_SIZE_BYTES)).profileImageKey();
        String second = handler.handle(command(JPEG, FILE_SIZE_BYTES)).profileImageKey();

        // then -> key가 같으면 이전 이미지를 덮어쓰고 업로드 검증도 무력해진다
        assertThat(first).isNotEqualTo(second);
    }

    @Test
    @DisplayName("허용하지 않는 형식이면 URL을 발급하지 않고 중단한다")
    void unsupportedMimeTypeStopsBeforeIssuing() {
        // when & then
        assertThatThrownBy(() -> handler.handle(command("image/gif", FILE_SIZE_BYTES)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(uploadUrlGenerator.isEmpty()).isTrue();
    }
}
